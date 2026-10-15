package io.github.seijikohara.femto.ui.assistant

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.seijikohara.femto.data.carassistant.CarActionPayload
import io.github.seijikohara.femto.data.carassistant.CarAssistantRepository
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.voice.VoiceRecognizer
import io.github.seijikohara.femto.data.voice.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class AssistantViewModel(
    application: Application,
) : ViewModel() {
    private val recognizer = VoiceRecognizer(application)
    private val carRepository = CarAssistantRepository(application)

    private val isThinking = MutableStateFlow(false)
    private val replyText = MutableStateFlow("")
    private val personaUsed = MutableStateFlow("hybrid")
    private val pendingAction = MutableStateFlow<CarActionPayload?>(null)

    val uiState: StateFlow<AssistantUiState> =
        combine(
            recognizer.state,
            isThinking,
            carRepository.isSpeaking,
            replyText,
            personaUsed,
            pendingAction,
        ) { args: Array<Any?> ->
            @Suppress("UNCHECKED_CAST")
            AssistantUiState(
                voice = args[0] as VoiceState,
                isThinking = args[1] as Boolean,
                isSpeaking = args[2] as Boolean,
                replyText = args[3] as String,
                personaUsed = args[4] as String,
                pendingAction = args[5] as CarActionPayload?,
            )
        }.stateIn(
            viewModelScope,
            WhileUiSubscribed,
            AssistantUiState(voice = recognizer.state.value),
        )

    init {
        // Auto-query ke Bernadya/Raisha saat pengemudi selesai berbicara
        viewModelScope.launch {
            recognizer.state.collect { state ->
                if (state is VoiceState.Result && state.text.isNotBlank() && !isThinking.value && replyText.value.isEmpty()) {
                    queryAssistant(state.text)
                }
            }
        }
    }

    fun onAction(action: AssistantAction) {
        when (action) {
            AssistantAction.StartListening -> {
                carRepository.stopAudio()
                replyText.value = ""
                pendingAction.value = null
                recognizer.start()
            }
            AssistantAction.StopListening -> recognizer.stop()
            AssistantAction.Reset -> {
                carRepository.stopAudio()
                replyText.value = ""
                pendingAction.value = null
                recognizer.reset()
            }
            is AssistantAction.SubmitQuery -> queryAssistant(action.text)
            AssistantAction.StopAudio -> carRepository.stopAudio()
        }
    }

    fun queryAssistant(userPrompt: String) {
        if (userPrompt.isBlank() || isThinking.value) return
        viewModelScope.launch {
            isThinking.value = true
            replyText.value = ""
            pendingAction.value = null

            val res = carRepository.query(
                message = userPrompt,
                personaPreference = "auto",
            )

            isThinking.value = false
            if (res != null) {
                replyText.value = res.text
                personaUsed.value = res.personaUsed
                pendingAction.value = res.action
            } else {
                replyText.value = "Maaf Bossfren, belum bisa terhubung ke server SalesPintar."
            }
        }
    }

    override fun onCleared() {
        recognizer.destroy()
        carRepository.release()
    }
}

internal class AssistantViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        @Suppress("UNCHECKED_CAST")
        return AssistantViewModel(application) as T
    }
}
