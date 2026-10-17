package io.github.seijikohara.femto.data.carassistant

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "CarAssistantApi"

internal class CarAssistantApi(
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build(),
    private val baseUrl: String = "https://novybot.aydaza.my.id/api/v1/car",
    private val apiKey: String = "mazda-cx5-car-key-2026",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    suspend fun checkHealth(): Boolean =
        withContext(ioDispatcher) {
            runCatching {
                val req =
                    Request
                        .Builder()
                        .url("$baseUrl/health")
                        .get()
                        .build()
                client.newCall(req).execute().use { res ->
                    res.isSuccessful
                }
            }.getOrDefault(false)
        }

    suspend fun sendVoicePrompt(requestPayload: CarAssistantRequest): CarAssistantResponse? =
        withContext(ioDispatcher) {
            runCatching {
                val jsonBody = json.encodeToString(CarAssistantRequest.serializer(), requestPayload)
                val body = jsonBody.toRequestBody(mediaTypeJson)

                val req =
                    Request
                        .Builder()
                        .url("$baseUrl/assistant")
                        .header("x-car-key", apiKey)
                        .post(body)
                        .build()

                client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string()
                        Log.e(TAG, "CarAssistantApi failed HTTP ${response.code}: $errBody")
                        return@use null
                    }

                    val respBody = response.body?.string() ?: return@use null
                    json.decodeFromString(CarAssistantResponse.serializer(), respBody)
                }
            }.onFailure { e ->
                Log.e(TAG, "CarAssistantApi network error: ${e.message}", e)
            }.getOrNull()
        }
}
