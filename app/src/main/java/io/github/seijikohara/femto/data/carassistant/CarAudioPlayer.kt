package io.github.seijikohara.femto.data.carassistant

import android.content.Context
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
    private var exoPlayer: ExoPlayer? = null
    private var textToSpeech: TextToSpeech? = null
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
                ExoPlayer.Builder(appContext)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_ASSISTANT)
                            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                            .build(),
                        true, // Otomatis audio-ducking musik latar
                    )
                    .setHandleAudioBecomingNoisy(true)
                    .build().apply {
                        addListener(
                            object : Player.Listener {
                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                                        _isPlaying.value = false
                                    }
                                }

                                override fun onIsPlayingChanged(playing: Boolean) {
                                    _isPlaying.value = playing
                                }
                            },
                        )
                    }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menginisialisasi ExoPlayer: ${e.message}", e)
        }
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale("id", "ID"))
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                textToSpeech?.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isPlaying.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isPlaying.value = false
                        }

                        override fun onError(utteranceId: String?) {
                            _isPlaying.value = false
                        }
                    },
                )
            } else {
                Log.w(TAG, "TextToSpeech init status not success: $status")
            }
        }
    }

    fun playReply(
        audioBase64: String?,
        fallbackText: String,
        onComplete: (() -> Unit)? = null,
    ) {
        stop()

        // 1. Prioritas Utama: Pemutaran Suara Cartesia via ExoPlayer
        if (!audioBase64.isNullOrBlank()) {
            try {
                val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                val tempFile = File(appContext.cacheDir, "car_assistant_reply.mp3")
                tempFile.writeBytes(audioBytes)

                val player = exoPlayer ?: return
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(tempFile)))
                player.prepare()
                player.play()
                _isPlaying.value = true
                return
            } catch (e: Exception) {
                Log.w(TAG, "Gagal memutar audio Cartesia base64: ${e.message}. Mencoba fallback ke TTS Android.")
            }
        }

        // 2. Fallback: On-Device TTS Android (Bahasa Indonesia)
        if (isTtsReady && textToSpeech != null && fallbackText.isNotBlank()) {
            try {
                _isPlaying.value = true
                textToSpeech?.speak(fallbackText, TextToSpeech.QUEUE_FLUSH, null, "car_assistant_voice")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memanggil TTS fallback: ${e.message}", e)
                _isPlaying.value = false
            }
        } else {
            _isPlaying.value = false
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
        _isPlaying.value = false
    }

    fun release() {
        stop()
        exoPlayer?.release()
        exoPlayer = null
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
