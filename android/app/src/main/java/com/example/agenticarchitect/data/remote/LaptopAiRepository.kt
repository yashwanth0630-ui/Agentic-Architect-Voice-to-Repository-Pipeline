package com.example.agenticarchitect.data.remote

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * 1. Request Payload sent from Android to Laptop FastAPI Backend
 */
data class PromptRequest(
    val prompt: String,
    val model: String = "qwen2.5-coder:1.5b",
    val temperature: Double = 0.2,
    val projectPath: String? = null,
    val useCursorAgent: Boolean? = null
)

/**
 * 2. Response Payload returned from Laptop Backend containing generated & verified code
 */
data class GenerateResponse(
    val status: String,
    val code: String,
    val model: String,
    val prompt: String,
    val latencyMs: Double,
    val filePath: String? = null,
    val filename: String? = null,
    val message: String = "",
    val syntaxValid: Boolean = true
)

/**
 * 3. Diagnostic Health Status from Laptop Bridge
 */
data class BridgeHealthStatus(
    val online: Boolean,
    val service: String = "Agentic-Architect-Laptop-Bridge",
    val version: String = "2.2.0",
    val ollamaOnline: Boolean = false,
    val availableModels: List<String> = emptyList(),
    val localIps: List<String> = emptyList(),
    val activeUrl: String = "",
    val message: String = ""
)

/**
 * 4. 3-State Connection Status for Android UI
 * 🟢 Connected to Idea Beacon Laptop
 * 🟡 Searching for laptop
 * 🔴 Laptop not found
 */
enum class ConnectionState {
    CONNECTED,
    SEARCHING,
    DISCONNECTED
}

/**
 * Structured Connection Details for UI badges and diagnostics
 */
data class ConnectionInfo(
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val endpointUrl: String = "",
    val message: String = "Make sure your phone and laptop are connected to the same Wi-Fi network and that the Idea Beacon Bridge is running.",
    val isWifiLan: Boolean = false,
    val isAdbFallback: Boolean = false,
    val version: String = "",
    val host: String = ""
)

/**
 * Structured Exception for Laptop Bridge communication errors with actionable remediation advice.
 */
class LaptopBridgeException(
    override val message: String,
    val remediationTip: String,
    val endpoint: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Repository to dispatch voice prompts to laptop backend over local Wi-Fi LAN or USB reverse tether.
 *
 * Connection Priority:
 * 1. Previously discovered/working LAN endpoint (Cached)
 * 2. Current laptop LAN endpoint (Discovered via UDP Broadcast / HTTP /discover)
 * 3. User manually entered LAN endpoint
 * 4. Existing ADB reverse fallback (http://localhost:8000)
 * 5. Android emulator fallback (http://10.0.2.2:8000)
 */
class LaptopAiRepository(
    private val context: Context? = null,
    initialBaseUrl: String = ""
) {
    companion object {
        const val DISCOVERY_UDP_PORT = 8001
        const val DISCOVERY_MAGIC_REQUEST = "IDEA_BEACON_DISCOVER"
        const val DISCOVERY_SERVICE_ID = "idea-beacon-bridge"
        const val DEFAULT_HTTP_PORT = 8000
        const val PREFS_NAME = "idea_beacon_laptop_prefs"
        const val KEY_LAST_WORKING = "last_working_endpoint"
        const val KEY_MANUAL_ADDRESS = "manual_endpoint"
        const val ADB_FALLBACK_URL = "http://localhost:8000"
        const val EMULATOR_FALLBACK_URL = "http://10.0.2.2:8000"
    }

    private val prefs: SharedPreferences? = try {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (_: Exception) {
        null
    }

    var lastWorkingEndpoint: String? = prefs?.getString(KEY_LAST_WORKING, null)
        private set

    var manualEndpoint: String? = prefs?.getString(KEY_MANUAL_ADDRESS, null)
        private set

    var discoveredEndpoint: String? = null
        private set

    var baseUrl: String = initialBaseUrl.trimEnd('/').ifEmpty {
        lastWorkingEndpoint ?: manualEndpoint ?: ADB_FALLBACK_URL
    }
        private set

    var connectionInfo: ConnectionInfo = ConnectionInfo()
        private set

    /**
     * Priority chain:
     * 1. Previously discovered/working LAN endpoint
     * 2. Current laptop LAN endpoint discovered on Wi-Fi
     * 3. User manual endpoint
     * 4. Currently configured baseUrl (if different)
     * 5. ADB reverse fallback (USB)
     * 6. Android emulator host fallback
     */
    val candidateUrls: List<String>
        get() {
            val list = mutableListOf<String>()
            lastWorkingEndpoint?.let { if (it.isNotBlank()) list.add(normalizeUrl(it)) }
            discoveredEndpoint?.let { if (it.isNotBlank()) list.add(normalizeUrl(it)) }
            manualEndpoint?.let { if (it.isNotBlank()) list.add(normalizeUrl(it)) }
            if (baseUrl.isNotBlank()) list.add(normalizeUrl(baseUrl))
            list.add(ADB_FALLBACK_URL)
            list.add(EMULATOR_FALLBACK_URL)
            return list.distinct()
        }

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "http://$trimmed"
        } else {
            trimmed
        }
    }

    /**
     * Validates an IP address or hostname and port for manual settings.
     * Accepts: "192.168.1.100:8000", "192.168.1.100", "http://192.168.1.100:8000"
     */
    fun validateAddress(rawInput: String): Result<String> {
        val cleaned = rawInput.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')

        if (cleaned.isBlank()) {
            return Result.failure(IllegalArgumentException("Address cannot be empty"))
        }

        val parts = cleaned.split(":")
        val host = parts[0].trim()
        val port = if (parts.size > 1) {
            val parsedPort = parts[1].trim().toIntOrNull()
            if (parsedPort == null || parsedPort !in 1..65535) {
                return Result.failure(IllegalArgumentException("Port must be between 1 and 65535"))
            }
            parsedPort
        } else {
            DEFAULT_HTTP_PORT
        }

        // Validate host format (IPv4 or valid domain/hostname)
        val ipv4Regex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        if (ipv4Regex.matches(host)) {
            val octets = host.split(".").map { it.toIntOrNull() ?: 999 }
            if (octets.any { it !in 0..255 }) {
                return Result.failure(IllegalArgumentException("Invalid IPv4 address octets ($host)"))
            }
        } else {
            // Check alphanumeric hostname (e.g., localhost, laptop.local)
            val hostnameRegex = Regex("""^[a-zA-Z0-9.\-_]+$""")
            if (!hostnameRegex.matches(host)) {
                return Result.failure(IllegalArgumentException("Invalid hostname format ($host)"))
            }
        }

        val normalized = "http://$host:$port"
        return Result.success(normalized)
    }

    /**
     * Saves manual laptop address setting. Pass blank or null to clear.
     */
    fun setManualAddress(rawInput: String?): Result<String> {
        if (rawInput.isNullOrBlank()) {
            manualEndpoint = null
            prefs?.edit()?.remove(KEY_MANUAL_ADDRESS)?.apply()
            return Result.success("")
        }

        val validation = validateAddress(rawInput)
        return validation.map { validUrl ->
            manualEndpoint = validUrl
            prefs?.edit()?.putString(KEY_MANUAL_ADDRESS, validUrl)?.apply()
            baseUrl = validUrl
            validUrl
        }
    }

    /**
     * Discovers Laptop Bridge on the local Wi-Fi network via UDP broadcast (port 8001).
     *
     * Protocol:
     * - Broadcast packet "IDEA_BEACON_DISCOVER" to 255.255.255.255:8001
     * - Laptop responds with JSON containing {"service":"idea-beacon-bridge","url":"..."}
     * - Timeout: 1500ms
     */
    suspend fun discoverViaUdp(timeoutMs: Int = 1500): Result<String> = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = timeoutMs
            }

            val sendData = DISCOVERY_MAGIC_REQUEST.toByteArray(Charsets.UTF_8)
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddr, DISCOVERY_UDP_PORT)
            socket.send(sendPacket)

            val recvBuffer = ByteArray(1024)
            val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)
            socket.receive(recvPacket)

            val reply = String(recvPacket.data, 0, recvPacket.length, Charsets.UTF_8).trim()
            val json = JSONObject(reply)

            if (json.optString("service") == DISCOVERY_SERVICE_ID) {
                val url = json.optString("url").ifEmpty {
                    val host = json.optString("host", recvPacket.address.hostAddress ?: "")
                    val port = json.optInt("port", DEFAULT_HTTP_PORT)
                    "http://$host:$port"
                }
                discoveredEndpoint = url
                baseUrl = url
                saveWorkingEndpoint(url)
                return@withContext Result.success(url)
            } else {
                return@withContext Result.failure(Exception("Unknown discovery service response"))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            socket?.close()
        }
    }

    /**
     * Probes an HTTP endpoint for /discover or /health
     */
    private suspend fun probeHttpEndpoint(targetUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val endpointUrl = URL("$targetUrl/discover")
            val conn = (endpointUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1200
                readTimeout = 1200
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                if (json.optString("service") == DISCOVERY_SERVICE_ID) {
                    return@withContext true
                }
            }
        } catch (_: Exception) {
            // Try /health as fallback probe
            try {
                val healthUrl = URL("$targetUrl/health")
                val conn = (healthUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 1000
                    readTimeout = 1000
                }
                if (conn.responseCode in 200..299) {
                    return@withContext true
                }
            } catch (_: Exception) {}
        }
        false
    }

    /**
     * Performs complete Wi-Fi discovery & health resolution.
     * Updates [connectionInfo] state (CONNECTED, SEARCHING, DISCONNECTED).
     */
    suspend fun checkConnection(): ConnectionInfo = withContext(Dispatchers.IO) {
        connectionInfo = connectionInfo.copy(state = ConnectionState.SEARCHING)

        // 1. Try fast UDP broadcast on Wi-Fi
        val udpResult = discoverViaUdp(timeoutMs = 1500)
        if (udpResult.isSuccess) {
            val endpoint = udpResult.getOrThrow()
            connectionInfo = ConnectionInfo(
                state = ConnectionState.CONNECTED,
                endpointUrl = endpoint,
                message = "Connected to Idea Beacon Laptop",
                isWifiLan = true,
                isAdbFallback = false
            )
            return@withContext connectionInfo
        }

        // 2. Try candidate list in strict priority order
        for (target in candidateUrls) {
            if (probeHttpEndpoint(target)) {
                baseUrl = target
                saveWorkingEndpoint(target)
                val isAdb = target.contains("localhost") || target.contains("127.0.0.1")
                val isLan = !isAdb && !target.contains("10.0.2.2")
                connectionInfo = ConnectionInfo(
                    state = ConnectionState.CONNECTED,
                    endpointUrl = target,
                    message = if (isLan) "Connected to Idea Beacon Laptop" else "Connected via ADB Reverse Tether",
                    isWifiLan = isLan,
                    isAdbFallback = isAdb
                )
                return@withContext connectionInfo
            }
        }

        // 3. Not reachable
        connectionInfo = ConnectionInfo(
            state = ConnectionState.DISCONNECTED,
            endpointUrl = baseUrl,
            message = "Make sure your phone and laptop are connected to the same Wi-Fi network and that the Idea Beacon Bridge is running.",
            isWifiLan = false,
            isAdbFallback = false
        )
        return@withContext connectionInfo
    }

    private fun saveWorkingEndpoint(url: String) {
        if (!url.contains("localhost") && !url.contains("127.0.0.1") && !url.contains("10.0.2.2")) {
            lastWorkingEndpoint = url
            prefs?.edit()?.putString(KEY_LAST_WORKING, url)?.apply()
        }
    }

    fun updateBaseUrl(newBaseUrl: String) {
        this.baseUrl = normalizeUrl(newBaseUrl)
    }

    /**
     * Executes prompt generation on laptop bridge, following priority chain:
     * Wi-Fi LAN -> Manual -> ADB Reverse -> Emulator.
     */
    suspend fun generateFromPrompt(
        prompt: String,
        model: String = "qwen2.5-coder:1.5b",
        temperature: Double = 0.2
    ): Result<GenerateResponse> = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) {
            return@withContext Result.failure(
                LaptopBridgeException(
                    message = "Prompt cannot be empty",
                    remediationTip = "Please speak or enter an architecture prompt.",
                    endpoint = baseUrl
                )
            )
        }

        var lastException: Throwable? = null

        // Try candidate endpoints in priority order
        for (targetUrl in candidateUrls) {
            try {
                val result = executeGenerateRequest(targetUrl, prompt, model, temperature)
                if (targetUrl != baseUrl) {
                    baseUrl = targetUrl
                }
                saveWorkingEndpoint(targetUrl)
                val isAdb = targetUrl.contains("localhost") || targetUrl.contains("127.0.0.1")
                connectionInfo = ConnectionInfo(
                    state = ConnectionState.CONNECTED,
                    endpointUrl = targetUrl,
                    message = if (isAdb) "Connected via ADB Reverse Tether" else "Connected to Idea Beacon Laptop",
                    isWifiLan = !isAdb && !targetUrl.contains("10.0.2.2"),
                    isAdbFallback = isAdb
                )
                return@withContext Result.success(result)
            } catch (e: ConnectException) {
                lastException = e
            } catch (e: UnknownHostException) {
                lastException = e
            } catch (e: Exception) {
                val remediation = when (e) {
                    is SocketTimeoutException -> "LLM inference took too long (>120s). Try a smaller model like qwen2.5-coder:1.5b."
                    else -> "Check laptop terminal running python scripts/laptop_ai_server.py."
                }
                return@withContext Result.failure(
                    LaptopBridgeException(
                        message = e.message ?: "Failed to generate code on laptop bridge",
                        remediationTip = remediation,
                        endpoint = targetUrl,
                        cause = e
                    )
                )
            }
        }

        connectionInfo = ConnectionInfo(
            state = ConnectionState.DISCONNECTED,
            endpointUrl = baseUrl,
            message = "Make sure your phone and laptop are connected to the same Wi-Fi network and that the Idea Beacon Bridge is running."
        )

        val remediation = "Could not reach laptop bridge at $baseUrl. " +
                "Make sure your phone and laptop are connected to the same Wi-Fi network and that the Idea Beacon Bridge is running. " +
                "Alternatively, enter your laptop's Wi-Fi IP in Settings or plug in USB and run 'adb reverse tcp:8000 tcp:8000'."

        Result.failure(
            LaptopBridgeException(
                message = "Laptop AI Bridge unreachable ($baseUrl)",
                remediationTip = remediation,
                endpoint = baseUrl,
                cause = lastException
            )
        )
    }

    private fun executeGenerateRequest(
        targetBaseUrl: String,
        prompt: String,
        model: String,
        temperature: Double
    ): GenerateResponse {
        val endpointUrl = URL("$targetBaseUrl/generate")
        val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; utf-8")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            connectTimeout = 6000
            readTimeout = 120000
        }

        val requestJson = JSONObject().apply {
            put("prompt", prompt)
            put("model", model)
            put("temperature", temperature)
        }

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(requestJson.toString())
            writer.flush()
        }

        val responseCode = connection.responseCode
        if (responseCode in 200..299) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val responseText = reader.use { it.readText() }
            val json = JSONObject(responseText)

            val valObj = json.optJSONObject("validation")
            val syntaxValid = valObj?.optBoolean("syntax_valid", true) ?: true

            return GenerateResponse(
                status = json.optString("status", "success"),
                code = json.optString("code", ""),
                model = json.optString("model", model),
                prompt = json.optString("prompt", prompt),
                latencyMs = json.optDouble("latency_ms", 0.0),
                filePath = json.optString("file_path", null),
                filename = json.optString("filename", null),
                message = json.optString("message", "Code generated successfully"),
                syntaxValid = syntaxValid
            )
        } else {
            val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
            throw Exception("HTTP $responseCode: ${errorStream ?: connection.responseMessage}")
        }
    }

    /**
     * Diagnostic health check against the laptop bridge.
     */
    suspend fun checkHealth(): Result<BridgeHealthStatus> = withContext(Dispatchers.IO) {
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/health")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                }

                if (connection.responseCode in 200..299) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val ollamaObj = json.optJSONObject("ollama")

                    val modelsList = mutableListOf<String>()
                    val modelsArray = ollamaObj?.optJSONArray("available_models")
                    if (modelsArray != null) {
                        for (i in 0 until modelsArray.length()) {
                            modelsList.add(modelsArray.getString(i))
                        }
                    }

                    val ipsList = mutableListOf<String>()
                    val ipsArray = json.optJSONArray("local_ips")
                    if (ipsArray != null) {
                        for (i in 0 until ipsArray.length()) {
                            ipsList.add(ipsArray.getString(i))
                        }
                    }

                    baseUrl = targetUrl
                    saveWorkingEndpoint(targetUrl)
                    return@withContext Result.success(
                        BridgeHealthStatus(
                            online = true,
                            service = json.optString("service", "Laptop Bridge"),
                            version = json.optString("version", "2.2.0"),
                            ollamaOnline = ollamaObj?.optBoolean("online", false) ?: false,
                            availableModels = modelsList,
                            localIps = ipsList,
                            activeUrl = targetUrl,
                            message = "Laptop Bridge online at $targetUrl"
                        )
                    )
                }
            } catch (_: Exception) {
                // Continue to next candidate
            }
        }

        Result.failure(
            LaptopBridgeException(
                message = "Laptop Bridge offline",
                remediationTip = "Make sure your phone and laptop are connected to the same Wi-Fi network and that python scripts/laptop_ai_server.py is running.",
                endpoint = baseUrl
            )
        )
    }
}
