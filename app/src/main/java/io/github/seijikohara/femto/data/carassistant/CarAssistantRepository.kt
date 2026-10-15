package io.github.seijikohara.femto.data.carassistant

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

internal class CarAssistantRepository(
    context: Context,
    private val api: CarAssistantApi = CarAssistantApi(),
    val audioPlayer: CarAudioPlayer = CarAudioPlayer(context),
) {
    val isSpeaking: StateFlow<Boolean> = audioPlayer.isPlaying

    suspend fun query(
        message: String,
        personaPreference: String = "auto",
        speedKmh: Double? = null,
        currentCity: String? = null,
    ): CarAssistantResponse? {
        val driverContext =
            if (speedKmh != null || currentCity != null) {
                DriverContext(speedKmh = speedKmh, currentCity = currentCity)
            } else {
                null
            }

        val request =
            CarAssistantRequest(
                message = message,
                personaPreference = personaPreference,
                driverContext = driverContext,
            )

        val response = api.sendVoicePrompt(request)
        if (response != null && response.text.isNotBlank()) {
            audioPlayer.playReply(
                audioBase64 = response.audioBase64,
                fallbackText = response.text,
            )
        }
        return response
    }

    fun stopAudio() {
        audioPlayer.stop()
    }

    fun release() {
        audioPlayer.release()
    }
}
