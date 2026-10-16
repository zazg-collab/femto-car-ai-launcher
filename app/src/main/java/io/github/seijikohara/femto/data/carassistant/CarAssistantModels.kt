package io.github.seijikohara.femto.data.carassistant

import kotlinx.serialization.Serializable

@Serializable
internal data class ConversationHistoryItem(
    val role: String, // "user" or "assistant"
    val content: String,
)

@Serializable
internal data class DriverLocation(
    val lat: Double,
    val lng: Double,
)

@Serializable
internal data class DriverContext(
    val speedKmh: Double? = null,
    val currentCity: String? = null,
    val currentLocation: DriverLocation? = null,
    val currentTrack: String? = null,
)

@Serializable
internal data class CarAssistantRequest(
    val message: String,
    val personaPreference: String = "auto",
    val conversationHistory: List<ConversationHistoryItem>? = null,
    val driverContext: DriverContext? = null,
)

@Serializable
internal data class CarActionPayload(
    val type: String, // "NAVIGATE", "CONTROL_MUSIC"
    val query: String? = null,
    val musicCommand: String? = null,
    val trackQuery: String? = null,
)

@Serializable
internal data class CarAssistantResponse(
    val ok: Boolean = false,
    val personaUsed: String = "hybrid", // "raisha", "bernadya", "hybrid"
    val text: String = "",
    val audioBase64: String? = null,
    val action: CarActionPayload? = null,
    val toolsInvoked: List<String> = emptyList(),
)
