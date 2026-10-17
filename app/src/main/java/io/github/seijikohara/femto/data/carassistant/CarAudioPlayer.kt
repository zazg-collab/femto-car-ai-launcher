package io.github.seijikohara.femto.data.carassistant

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

private const val TAG = "CarAudioPlayer"

internal class CarAudioPlayer(
    private val context: Context,
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var exoPlayer: ExoPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var toneGenerator: ToneGenerator? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isTtsReady = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        initExoPlayer()
        initTextToSpeech()
    }

    private fun initExoPlayer() {
        try {
            exoPlayer =
                ExoPlayer
                    .Builder(appContext)
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(C.USAGE_ASSISTANT)
                            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                            .build(),
                        false, // Explicit audio-ducking via requestDuckingFocus()
                    ).setHandleAudioBecomingNoisy(true)
                    .build()
                    .apply {
                        addListener(
                            object : Player.Listener {
                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                                        _isPlaying.value = false
                                        abandonDuckingFocus()
                                    }
                                }

                                override fun onIsPlayingChanged(playing: Boolean) {
                                    _isPlaying.value = playing
                                    if (!playing && playbackState != Player.STATE_BUFFERING) {
                                        abandonDuckingFocus()
                                    }
                                }
                            },
                        )
                    }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menginisialisasi ExoPlayer: ${e.message}", e)
        }
    }

    private fun initTextToSpeech() {
        textToSpeech =
            TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = textToSpeech?.setLanguage(Locale("id", "ID"))
                    isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED

                    // Konfigurasi audio attributes USAGE_ASSISTANT agar TTS tidak meminta exclusive focus yang mem-pause musik
                    val ttsAudioAttributes =
                        android.media.AudioAttributes
                            .Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    textToSpeech?.setAudioAttributes(ttsAudioAttributes)

                    textToSpeech?.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                _isPlaying.value = true
                            }

                            override fun onDone(utteranceId: String?) {
                                _isPlaying.value = false
                                abandonDuckingFocus()
                            }

                            override fun onError(utteranceId: String?) {
                                _isPlaying.value = false
                                abandonDuckingFocus()
                            }
                        },
                    )
                } else {
                    Log.w(TAG, "TextToSpeech init status not success: $status")
                }
            }
    }

    private fun requestDuckingFocus() {
        try {
            if (audioFocusRequest == null) {
                val focusAttributes =
                    android.media.AudioAttributes
                        .Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                audioFocusRequest =
                    AudioFocusRequest
                        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(focusAttributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setOnAudioFocusChangeListener { /* no-op */ }
                        .build()
            }
            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal request audio focus ducking: ${e.message}")
        }
    }

    private fun abandonDuckingFocus() {
        try {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal abandon audio focus ducking: ${e.message}")
        }
    }

    private fun playWakeupChime() {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
            }
            // Nada earcon singkat (120ms) untuk membangunkan DSP Mazda CX-5 secara instan
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 120)
        } catch (e: Exception) {
            Log.w(TAG, "Gagal memutar earcon chime: ${e.message}")
        }
    }

    fun playReply(
        audioBase64: String?,
        fallbackText: String,
        onComplete: (() -> Unit)? = null,
    ) {
        stop()

        // 1. Minta Audio Focus Ducking: Spotify otomatis meredupkan volume (ducking ~20%), tidak dipause/distop
        requestDuckingFocus()

        // 2. Mainkan Earcon Chime: Bangunkan amplifier Mazda CX-5 (<120ms) agar kata pertama tidak terpotong
        playWakeupChime()

        // 3. Prioritas Utama: Pemutaran Suara Natural via ExoPlayer (Cartesia / Gemini TTS)
        if (!audioBase64.isNullOrBlank()) {
            try {
                val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                val isWav =
                    audioBytes.size >= 4 &&
                        audioBytes[0] == 'R'.code.toByte() &&
                        audioBytes[1] == 'I'.code.toByte() &&
                        audioBytes[2] == 'F'.code.toByte() &&
                        audioBytes[3] == 'F'.code.toByte()
                val extension = if (isWav) "wav" else "mp3"
                val tempFile = File(appContext.cacheDir, "car_assistant_reply.$extension")
                tempFile.writeBytes(audioBytes)

                val player = exoPlayer ?: return
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(tempFile)))
                player.prepare()
                player.play()
                _isPlaying.value = true
                return
            } catch (e: Exception) {
                Log.w(TAG, "Gagal memutar audio assistant base64: ${e.message}. Mencoba fallback ke TTS Android.")
            }
        }

        // 4. Fallback: On-Device TTS Android (Bahasa Indonesia) tanpa dead-silence delay
        if (isTtsReady && textToSpeech != null && fallbackText.isNotBlank()) {
            try {
                _isPlaying.value = true
                textToSpeech?.speak(fallbackText, TextToSpeech.QUEUE_FLUSH, null, "car_assistant_voice")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memanggil TTS fallback: ${e.message}", e)
                _isPlaying.value = false
                abandonDuckingFocus()
            }
        } else {
            _isPlaying.value = false
            abandonDuckingFocus()
        }
    }

    fun stop() {
        try {
            exoPlayer?.stop()
            if (textToSpeech?.isSpeaking == true) {
                textToSpeech?.stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio: ${e.message}")
        }
        abandonDuckingFocus()
        _isPlaying.value = false
    }

    fun release() {
        stop()
        exoPlayer?.release()
        exoPlayer = null
        textToSpeech?.shutdown()
        textToSpeech = null
        toneGenerator?.release()
        toneGenerator = null
    }
}
