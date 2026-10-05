package com.example.agenticarchitect.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 1. Request Payload sent from Android to Laptop FastAPI Backend
 */
data class PromptRequest(
    val prompt: String,
    val model: String = "qwen2.5-coder:7b",
    val temperature: Double = 0.2
)

/**
 * 2. Response Payload returned from Laptop Backend containing generated code
 */
data class GenerateResponse(
    val status: String,
    val code: String,
    val model: String,
    val prompt: String,
    val latencyMs: Double
)

/**
 * Retrofit Interface Definition (for projects using Retrofit + Gson/Moshi/Serialization)
 *
 * interface LaptopAiApiService {
 *     @POST("generate")
 *     suspend fun generateCode(
 *         @Body request: PromptRequest
 *     ): GenerateResponse
 * }
 */

/**
 * Repository to dispatch voice prompts to laptop backend over local Wi-Fi.
 * Supports both standard HTTP connection (zero extra dependencies needed)
 * and configurable base URLs.
 */
class LaptopAiRepository(
    private var baseUrl: String = "http://192.168.1.100:8000" // Replace with laptop IP or use adb reverse
) {

    fun updateBaseUrl(newBaseUrl: String) {
        this.baseUrl = newBaseUrl.trimEnd('/')
    }

    suspend fun generateFromPrompt(
        prompt: String,
        model: String = "qwen2.5-coder:7b"
    ): Result<GenerateResponse> = withContext(Dispatchers.IO) {
        try {
            val endpointUrl = URL("$baseUrl/generate")
            val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; utf-8")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 15000  // 15 seconds connection timeout
                readTimeout = 120000    // 120 seconds read timeout (allows LLM GPU generation)
            }

            // Construct JSON request body
            val requestJson = JSONObject().apply {
                put("prompt", prompt)
                put("model", model)
                put("temperature", 0.2)
            }

            // Write payload
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val responseText = reader.use { it.readText() }
                val json = JSONObject(responseText)

                val result = GenerateResponse(
                    status = json.optString("status", "success"),
                    code = json.optString("code", ""),
                    model = json.optString("model", model),
                    prompt = json.optString("prompt", prompt),
                    latencyMs = json.optDouble("latency_ms", 0.0)
                )
                Result.success(result)
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Result.failure(Exception("HTTP $responseCode: ${errorStream ?: connection.responseMessage}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkHealth(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpointUrl = URL("$baseUrl/health")
            val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            val isSuccess = connection.responseCode in 200..299
            Result.success(isSuccess)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
