package io.github.seijikohara.femto.ui.assistant

import io.github.seijikohara.femto.data.carassistant.CarActionPayload
import io.github.seijikohara.femto.data.voice.VoiceState

/** State for the assistant sheet: in-launcher voice recognition & AI conversation step. */
internal data class AssistantUiState(
    val voice: VoiceState,
    val isThinking: Boolean = false,
    val isSpeaking: Boolean = false,
    val replyText: String = "",
    val personaUsed: String = "hybrid", // "raisha", "bernadya", "hybrid"
    val pendingAction: CarActionPayload? = null,
)

/** Intents the assistant sheet reports up to its ViewModel. */
internal sealed interface AssistantAction {
    data object StartListening : AssistantAction

    data object StopListening : AssistantAction

    /** Return to the ready state, discarding the current result or error. */
    data object Reset : AssistantAction

    /** Send voice transcript query to SalesPintar Car Assistant */
    data class SubmitQuery(
        val text: String,
    ) : AssistantAction

    /** Stop voice playback */
    data object StopAudio : AssistantAction
}
