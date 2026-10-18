package io.github.seijikohara.femto.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import io.github.seijikohara.femto.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "VoiceRecognizer"

/**
 * One step of the in-launcher voice flow. [Unavailable] means the device has no
 * speech recognizer (e.g. a head unit without Google services), in which case
 * the assistant sheet falls back to system-intent delegation.
 */
internal sealed interface VoiceState {
    data object Unavailable : VoiceState

    data object Idle : VoiceState

    data class Listening(
        val partial: String,
    ) : VoiceState

    data class Result(
        val text: String,
    ) : VoiceState

    data class Failed(
        val messageRes: Int,
    ) : VoiceState
}

/**
 * The in-process speech input a ViewModel drives: the recognition lifecycle as
 * a [state] flow plus its controls. [VoiceRecognizer] is the platform
 * implementation; tests substitute a fake, because Robolectric has no speech
 * engine to drive.
 */
internal interface SpeechInput {
    val state: StateFlow<VoiceState>

    fun start()

    fun stop()

    /** Return to the ready state, discarding any result or error. */
    fun reset()

    fun destroy()
}

/**
 * Thin wrapper over the platform [SpeechRecognizer] that exposes the recognition
 * lifecycle as a [StateFlow]. The launcher hosts the mic UI itself (rather than
 * firing `ACTION_ASSIST` and leaving), so speech is captured in-process and the
 * partial transcript streams to the sheet as the user speaks.
 *
 * Must be created and driven on the main thread — [SpeechRecognizer] requires
 * it. The owning ViewModel calls [start] / [stop] from Compose (main) and
 * [destroy] from `onCleared`.
 */
internal class VoiceRecognizer(
    context: Context,
) : SpeechInput {
    private val appContext = context.applicationContext
    val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    private val _state =
        MutableStateFlow<VoiceState>(if (isAvailable) VoiceState.Idle else VoiceState.Unavailable)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    override fun start() {
        if (!isAvailable) return
        destroyRecognizer()

        try {
            val fresh =
                SpeechRecognizer.createSpeechRecognizer(appContext).also {
                    it.setRecognitionListener(listener)
                    recognizer = it
                }
            _state.value = VoiceState.Listening(partial = "")
            fresh.startListening(recognizerIntent())
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menginisialisasi fresh SpeechRecognizer: ${e.message}", e)
            _state.value = VoiceState.Failed(messageRes = R.string.assistant_voice_error_generic)
        }
    }

    override fun stop() {
        try {
            recognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping SpeechRecognizer: ${e.message}")
        }
    }

    override fun reset() {
        if (isAvailable) {
            destroyRecognizer()
            _state.value = VoiceState.Idle
        }
    }

    override fun destroy() {
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying SpeechRecognizer: ${e.message}")
        } finally {
            recognizer = null
        }
    }

    private fun recognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("id-ID", "en-US"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }

    private val listener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = VoiceState.Listening(partial = "")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                firstText(partialResults)?.takeIf { it.isNotBlank() }?.let {
                    _state.value = VoiceState.Listening(partial = it)
                }
            }

            override fun onResults(results: Bundle?) {
                _state.value =
                    firstText(results)?.takeIf { it.isNotBlank() }?.let { VoiceState.Result(it) }
                        ?: VoiceState.Idle
            }

            override fun onError(error: Int) {
                // messageFor collapses codes into three strings; keep the raw
                // platform code for diagnosis.
                Log.w(TAG, "speech recognition error=$error (${errorName(error)})")
                _state.value = VoiceState.Failed(messageRes = messageFor(error))
            }

            override fun onEndOfSpeech() = Unit

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEvent(
                eventType: Int,
                params: Bundle?,
            ) = Unit
        }

    internal fun firstText(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    internal fun messageFor(error: Int): Int =
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            -> R.string.assistant_voice_error_no_match

            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            -> R.string.assistant_voice_error_network

            else -> R.string.assistant_voice_error_generic
        }

    internal fun errorName(error: Int): String =
        when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "ERROR_TOO_MANY_REQUESTS"
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "ERROR_SERVER_DISCONNECTED"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "ERROR_LANGUAGE_NOT_SUPPORTED"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "ERROR_LANGUAGE_UNAVAILABLE"
            SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "ERROR_CANNOT_CHECK_SUPPORT"
            else -> "ERROR_UNKNOWN($error)"
        }
}
