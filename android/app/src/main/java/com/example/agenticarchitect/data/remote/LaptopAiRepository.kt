package com.example.agenticarchitect.data.remote

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    val code: String = "",
    val model: String = "",
    val prompt: String = "",
    val latencyMs: Double = 0.0,
    val filePath: String? = null,
    val filename: String? = null,
    val message: String = "",
    val syntaxValid: Boolean = true,
    val success: Boolean = true,
    val projectId: String? = null,
    val projectName: String? = null,
    val workspace: String? = null,
    val previewUrl: String? = null,
    val previewPort: Int? = null,
    val filesCreated: List<String> = emptyList()
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
open class LaptopAiRepository(
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
        const val KEY_SELECTED_REPO_OWNER = "selected_repo_owner"
        const val KEY_SELECTED_REPO_NAME = "selected_repo_name"
        const val KEY_SELECTED_REPO_FULL_NAME = "selected_repo_full_name"
        const val KEY_SELECTED_REPO_URL = "selected_repo_url"
        const val KEY_SELECTED_REPO_BRANCH = "selected_repo_branch"
        const val KEY_SELECTED_REPO_PRIVATE = "selected_repo_private"
        const val DEFAULT_INSTRUCT_READ_TIMEOUT_MS = 120000
    }

    private val prefs: SharedPreferences? = try {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (_: Exception) {
        null
    }

    var selectedRepository: SelectedRepository? = run {
        val fullName = prefs?.getString(KEY_SELECTED_REPO_FULL_NAME, null)
        val owner = prefs?.getString(KEY_SELECTED_REPO_OWNER, null)
        val name = prefs?.getString(KEY_SELECTED_REPO_NAME, null)
        val url = prefs?.getString(KEY_SELECTED_REPO_URL, null)
        val branch = prefs?.getString(KEY_SELECTED_REPO_BRANCH, "main") ?: "main"
        val isPriv = prefs?.getBoolean(KEY_SELECTED_REPO_PRIVATE, false) ?: false
        if (!fullName.isNullOrBlank() && !name.isNullOrBlank() && !owner.isNullOrBlank() && !url.isNullOrBlank()) {
            SelectedRepository(
                owner = owner,
                name = name,
                fullName = fullName,
                htmlUrl = url,
                defaultBranch = branch,
                isPrivate = isPriv
            )
        } else {
            null
        }
    }
        private set

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

            val filesList = mutableListOf<String>()
            json.optJSONArray("files_created")?.let { arr ->
                for (i in 0 until arr.length()) {
                    filesList.add(arr.getString(i))
                }
            }

            val filePath = if (json.has("file_path") && !json.isNull("file_path")) {
                json.getString("file_path")
            } else if (json.has("workspace") && !json.isNull("workspace")) {
                json.getString("workspace")
            } else {
                null
            }

            val fileName = if (json.has("filename") && !json.isNull("filename")) {
                json.getString("filename")
            } else if (json.has("project_name") && !json.isNull("project_name")) {
                json.getString("project_name")
            } else {
                null
            }

            return GenerateResponse(
                status = json.optString("status", "success"),
                code = json.optString("code", ""),
                model = json.optString("model", model),
                prompt = json.optString("prompt", prompt),
                latencyMs = json.optDouble("latency_ms", 0.0),
                filePath = filePath,
                filename = fileName,
                message = json.optString("message", "Operation completed successfully"),
                syntaxValid = syntaxValid,
                success = json.optBoolean("success", true),
                projectId = if (json.has("project_id") && !json.isNull("project_id")) json.getString("project_id") else null,
                projectName = if (json.has("project_name") && !json.isNull("project_name")) json.getString("project_name") else null,
                workspace = if (json.has("workspace") && !json.isNull("workspace")) json.getString("workspace") else null,
                previewUrl = if (json.has("preview_url") && !json.isNull("preview_url")) json.getString("preview_url") else null,
                previewPort = if (json.has("preview_port") && !json.isNull("preview_port")) json.getInt("preview_port") else null,
                filesCreated = filesList
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

    // =========================================================================
    // GITHUB FOUNDATION NETWORKING (PHASE 2A)
    // =========================================================================

    private fun parseGitHubUser(userObj: JSONObject?): GitHubUser? {
        if (userObj == null) return null
        return GitHubUser(
            login = userObj.optString("login", ""),
            name = if (userObj.has("name") && !userObj.isNull("name")) userObj.getString("name") else null,
            avatarUrl = if (userObj.has("avatar_url") && !userObj.isNull("avatar_url")) userObj.getString("avatar_url") else null,
            htmlUrl = if (userObj.has("html_url") && !userObj.isNull("html_url")) userObj.getString("html_url") else null,
            bio = if (userObj.has("bio") && !userObj.isNull("bio")) userObj.getString("bio") else null,
            publicRepos = userObj.optInt("public_repos", 0)
        )
    }

    private fun parseGitHubRepoItem(repoObj: JSONObject): GitHubRepositoryItem {
        return GitHubRepositoryItem(
            name = repoObj.optString("name", ""),
            fullName = repoObj.optString("full_name", ""),
            isPrivate = repoObj.optBoolean("private", false),
            defaultBranch = repoObj.optString("default_branch", "main"),
            htmlUrl = repoObj.optString("html_url", ""),
            cloneUrl = repoObj.optString("clone_url", ""),
            description = if (repoObj.has("description") && !repoObj.isNull("description")) repoObj.getString("description") else null,
            updatedAt = if (repoObj.has("updated_at") && !repoObj.isNull("updated_at")) repoObj.getString("updated_at") else null,
            language = if (repoObj.has("language") && !repoObj.isNull("language")) repoObj.getString("language") else null,
            stargazersCount = repoObj.optInt("stargazers_count", 0),
            fork = repoObj.optBoolean("fork", false)
        )
    }

    /**
     * Checks GitHub authentication readiness on the laptop bridge.
     */
    suspend fun getGitHubStatus(): Result<GitHubStatusResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/github/status")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 3000
                    readTimeout = 8000
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val userObj = json.optJSONObject("user")
                    val user = parseGitHubUser(userObj)
                    return@withContext Result.success(
                        GitHubStatusResponse(
                            success = json.optBoolean("success", true),
                            authenticated = json.optBoolean("authenticated", false),
                            user = user,
                            message = if (json.has("message") && !json.isNull("message")) json.getString("message") else null
                        )
                    )
                } else if (responseCode == 401) {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    val msg = errorBody ?: "GitHub authentication required"
                    return@withContext Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(
            LaptopBridgeException(
                message = "Could not check GitHub status",
                remediationTip = "Make sure your phone and laptop are on the same Wi-Fi network and the laptop server is running.",
                endpoint = baseUrl,
                cause = lastException
            )
        )
    }

    /**
     * Retrieves the list of accessible repositories for the authenticated GitHub user.
     */
    suspend fun getGitHubRepositories(
        visibility: String = "all",
        sort: String = "updated",
        perPage: Int = 30,
        page: Int = 1
    ): Result<List<GitHubRepositoryItem>> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val query = "visibility=$visibility&sort=$sort&per_page=$perPage&page=$page"
                val endpointUrl = URL("$targetUrl/github/repos?$query")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 4000
                    readTimeout = 12000
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val reposList = mutableListOf<GitHubRepositoryItem>()
                    val arr = json.optJSONArray("repositories")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            reposList.add(parseGitHubRepoItem(arr.getJSONObject(i)))
                        }
                    }
                    return@withContext Result.success(reposList)
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    val errorMsg = if (errorBody != null) {
                        try {
                            val errJson = JSONObject(errorBody)
                            errJson.optJSONObject("error")?.optString("message") ?: errorBody
                        } catch (_: Exception) {
                            errorBody
                        }
                    } else {
                        "HTTP $responseCode: ${connection.responseMessage}"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(
            LaptopBridgeException(
                message = "Could not list GitHub repositories",
                remediationTip = "Check that your laptop bridge is running and GITHUB_TOKEN is configured.",
                endpoint = baseUrl,
                cause = lastException
            )
        )
    }

    /**
     * Retrieves normalized metadata for a specific repository: {owner}/{repo}.
     */
    suspend fun getGitHubRepository(owner: String, repo: String): Result<GitHubRepositoryItem> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/github/repos/$owner/$repo")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 4000
                    readTimeout = 12000
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val repoObj = json.optJSONObject("repository")
                    if (repoObj != null) {
                        return@withContext Result.success(parseGitHubRepoItem(repoObj))
                    } else {
                        return@withContext Result.failure(Exception("Missing repository object in response"))
                    }
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    val errorMsg = if (errorBody != null) {
                        try {
                            val errJson = JSONObject(errorBody)
                            errJson.optJSONObject("error")?.optString("message") ?: errorBody
                        } catch (_: Exception) {
                            errorBody
                        }
                    } else {
                        "HTTP $responseCode: ${connection.responseMessage}"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(
            LaptopBridgeException(
                message = "Could not fetch repository $owner/$repo",
                remediationTip = "Check that your laptop bridge is running and the repository exists.",
                endpoint = baseUrl,
                cause = lastException
            )
        )
    }

    // =========================================================================
    // REPOSITORY SELECTION PERSISTENCE
    // =========================================================================

    fun saveSelectedRepository(repo: GitHubRepositoryItem) {
        val parts = repo.fullName.split("/")
        val owner = if (parts.size > 1) parts[0] else ""
        val item = SelectedRepository(
            owner = owner.ifEmpty { repo.name },
            name = repo.name,
            fullName = repo.fullName,
            htmlUrl = repo.htmlUrl,
            defaultBranch = repo.defaultBranch,
            isPrivate = repo.isPrivate
        )
        selectedRepository = item
        prefs?.edit()?.apply {
            putString(KEY_SELECTED_REPO_OWNER, item.owner)
            putString(KEY_SELECTED_REPO_NAME, item.name)
            putString(KEY_SELECTED_REPO_FULL_NAME, item.fullName)
            putString(KEY_SELECTED_REPO_URL, item.htmlUrl)
            putString(KEY_SELECTED_REPO_BRANCH, item.defaultBranch)
            putBoolean(KEY_SELECTED_REPO_PRIVATE, item.isPrivate)
            apply()
        }
    }

    fun clearSelectedRepository() {
        selectedRepository = null
        prefs?.edit()?.apply {
            remove(KEY_SELECTED_REPO_OWNER)
            remove(KEY_SELECTED_REPO_NAME)
            remove(KEY_SELECTED_REPO_FULL_NAME)
            remove(KEY_SELECTED_REPO_URL)
            remove(KEY_SELECTED_REPO_BRANCH)
            remove(KEY_SELECTED_REPO_PRIVATE)
            apply()
        }
    }

    // =========================================================================
    // GITHUB DEVICE AUTHORIZATION FLOW (RFC 8628)
    // =========================================================================

    suspend fun requestDeviceCode(clientId: String? = null): Result<GitHubDeviceCodeResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/github/auth/device/code")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; utf-8")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 12000
                }

                val body = JSONObject().apply {
                    if (!clientId.isNullOrBlank()) {
                        put("client_id", clientId)
                    }
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    return@withContext Result.success(
                        GitHubDeviceCodeResponse(
                            success = json.optBoolean("success", true),
                            deviceCode = if (json.has("device_code")) json.getString("device_code") else null,
                            userCode = if (json.has("user_code")) json.getString("user_code") else null,
                            verificationUri = if (json.has("verification_uri")) json.getString("verification_uri") else "https://github.com/login/device",
                            expiresIn = json.optInt("expires_in", 900),
                            interval = json.optInt("interval", 5),
                            message = if (json.has("message")) json.getString("message") else null
                        )
                    )
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    val msg = if (errText != null) {
                        try {
                            val errJson = JSONObject(errText)
                            errJson.optJSONObject("error")?.optString("message") ?: errText
                        } catch (_: Exception) {
                            errText
                        }
                    } else "HTTP $responseCode"
                    return@withContext Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(
            LaptopBridgeException(
                message = "Could not initiate GitHub Device Authorization",
                remediationTip = "Check that your laptop bridge is running and reachable.",
                endpoint = baseUrl,
                cause = lastException
            )
        )
    }

    suspend fun pollDeviceCode(deviceCode: String, clientId: String? = null): Result<GitHubDevicePollResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/github/auth/device/poll")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; utf-8")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 12000
                }

                val body = JSONObject().apply {
                    put("device_code", deviceCode)
                    if (!clientId.isNullOrBlank()) {
                        put("client_id", clientId)
                    }
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val userObj = json.optJSONObject("user")
                    return@withContext Result.success(
                        GitHubDevicePollResponse(
                            success = json.optBoolean("success", true),
                            status = json.optString("status", "pending"),
                            authenticated = json.optBoolean("authenticated", false),
                            user = parseGitHubUser(userObj),
                            interval = if (json.has("interval")) json.getInt("interval") else null,
                            message = if (json.has("message")) json.getString("message") else null
                        )
                    )
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    val msg = if (errText != null) {
                        try {
                            val errJson = JSONObject(errText)
                            errJson.optJSONObject("error")?.optString("message") ?: errText
                        } catch (_: Exception) {
                            errText
                        }
                    } else "HTTP $responseCode"
                    return@withContext Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(
            LaptopBridgeException(
                message = "Could not poll GitHub Device Authorization status",
                remediationTip = "Check that your laptop bridge is running and reachable.",
                endpoint = baseUrl,
                cause = lastException
            )
        )
    }

    suspend fun logoutGitHub(): Result<Boolean> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/github/auth/logout")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 3000
                    readTimeout = 6000
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    clearSelectedRepository()
                    return@withContext Result.success(true)
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        clearSelectedRepository()
        Result.failure(
            LaptopBridgeException(
                message = "Could not logout from GitHub on laptop server",
                remediationTip = "Check that your laptop bridge is running.",
                endpoint = baseUrl,
                cause = lastException
            )
        )
    }

    // =========================================================================
    // PHASE 2C: REPOSITORY WORKSPACE & AI EDITING API METHODS
    // =========================================================================

    private fun parseWorkspaceRecord(json: JSONObject): RepoWorkspaceRecord {
        return RepoWorkspaceRecord(
            workspace_id = json.optString("workspace_id", ""),
            owner = json.optString("owner", ""),
            repo = json.optString("repo", ""),
            clone_url = json.optString("clone_url", ""),
            default_branch = json.optString("default_branch", "main"),
            workspace_path = json.optString("workspace_path", ""),
            baseline_commit = json.optString("baseline_commit", ""),
            current_branch = json.optString("current_branch", "main"),
            status = json.optString("status", "ready"),
            preview_port = if (json.has("preview_port") && !json.isNull("preview_port")) json.getInt("preview_port") else null,
            preview_url = if (json.has("preview_url") && !json.isNull("preview_url")) json.getString("preview_url") else null
        )
    }

    private fun parseChangeSummary(json: JSONObject?): RepoChangeSummary {
        if (json == null) return RepoChangeSummary()
        val createdList = mutableListOf<String>()
        val createdArr = json.optJSONArray("files_created")
        if (createdArr != null) {
            for (i in 0 until createdArr.length()) {
                createdList.add(createdArr.getString(i))
            }
        }

        val modifiedList = mutableListOf<String>()
        val modifiedArr = json.optJSONArray("files_modified")
        if (modifiedArr != null) {
            for (i in 0 until modifiedArr.length()) {
                modifiedList.add(modifiedArr.getString(i))
            }
        }

        val deletedList = mutableListOf<String>()
        val deletedArr = json.optJSONArray("files_deleted")
        if (deletedArr != null) {
            for (i in 0 until deletedArr.length()) {
                deletedList.add(deletedArr.getString(i))
            }
        }

        return RepoChangeSummary(
            files_created = createdList,
            files_modified = modifiedList,
            files_deleted = deletedList,
            total_changes = json.optInt("total_changes", 0),
            diff = json.optString("diff", ""),
            baseline_commit = json.optString("baseline_commit", "")
        )
    }

    fun extractErrorMessage(errText: String?, responseCode: Int): String {
        if (errText.isNullOrBlank()) return "HTTP $responseCode"
        try {
            val errJson = JSONObject(errText)

            // 1. Check RFC 7807 / standard error object: error.message
            val errorObj = errJson.optJSONObject("error")
            val errorObjMsg = errorObj?.optString("message")
            if (!errorObjMsg.isNullOrBlank()) {
                return errorObjMsg
            }

            // 2. Check FastAPI standard HTTPException detail string or array
            if (errJson.has("detail") && !errJson.isNull("detail")) {
                val detailObj = errJson.get("detail")
                when (detailObj) {
                    is String -> if (detailObj.isNotBlank()) return detailObj
                    is JSONArray -> {
                        val messages = mutableListOf<String>()
                        for (i in 0 until detailObj.length()) {
                            val item = detailObj.optJSONObject(i)
                            val msg = item?.optString("msg") ?: detailObj.optString(i)
                            if (msg.isNotBlank()) messages.add(msg)
                        }
                        if (messages.isNotEmpty()) return messages.joinToString("; ")
                    }
                    else -> {
                        val str = detailObj.toString()
                        if (str.isNotBlank()) return str
                    }
                }
            }

            // 3. Check top-level message
            val topMsg = errJson.optString("message")
            if (!topMsg.isNullOrBlank() && topMsg != "null") {
                return topMsg
            }
        } catch (_: Throwable) {
            // Fall through to regex extraction
        }

        // Regex fallback for host JVM unit test runners where Android JSONObject is stubbed
        val errorMsgRegex = Regex(""""error"\s*:\s*\{[^}]*"message"\s*:\s*"([^"]+)"""")
        val matchErrorMsg = errorMsgRegex.find(errText)
        if (matchErrorMsg != null) {
            return matchErrorMsg.groupValues[1]
        }

        val detailRegex = Regex(""""detail"\s*:\s*"([^"]+)"""")
        val matchDetail = detailRegex.find(errText)
        if (matchDetail != null) {
            return matchDetail.groupValues[1]
        }

        val msgFieldRegex = Regex(""""msg"\s*:\s*"([^"]+)"""")
        val matchMsgField = msgFieldRegex.find(errText)
        if (matchMsgField != null) {
            return matchMsgField.groupValues[1]
        }

        val generalMessageRegex = Regex(""""message"\s*:\s*"([^"]+)"""")
        val matchGeneralMsg = generalMessageRegex.find(errText)
        if (matchGeneralMsg != null) {
            return matchGeneralMsg.groupValues[1]
        }

        return "HTTP $responseCode: $errText"
    }

    suspend fun initRepoWorkspace(
        owner: String,
        repo: String,
        defaultBranch: String? = null
    ): Result<RepoWorkspaceRecord> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/repo/workspace/init")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; utf-8")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 120000 // Clone might take some time
                }

                val body = JSONObject().apply {
                    put("owner", owner)
                    put("repo", repo)
                    if (!defaultBranch.isNullOrBlank()) {
                        put("default_branch", defaultBranch)
                    }
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val wsObj = json.optJSONObject("workspace")
                    if (wsObj != null) {
                        return@withContext Result.success(parseWorkspaceRecord(wsObj))
                    } else {
                        return@withContext Result.failure(Exception("Missing workspace in response"))
                    }
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    val msg = extractErrorMessage(errText, responseCode)
                    return@withContext Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(
            LaptopBridgeException(
                message = "Could not initialize repository workspace on laptop server",
                remediationTip = "Check that your laptop bridge is running and reachable over Wi-Fi.",
                endpoint = baseUrl,
                cause = lastException
            )
        )
    }

    suspend fun getRepoWorkspaceStatus(workspaceId: String): Result<RepoWorkspaceStatusResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/repo/workspace/$workspaceId/status")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 4000
                    readTimeout = 8000
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val wsObj = json.optJSONObject("workspace")
                    val wsRecord = if (wsObj != null) parseWorkspaceRecord(wsObj) else null
                    return@withContext Result.success(
                        RepoWorkspaceStatusResponse(
                            success = json.optBoolean("success", true),
                            workspace = wsRecord,
                            message = if (json.has("message")) json.getString("message") else null
                        )
                    )
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    val msg = extractErrorMessage(errText, responseCode)
                    return@withContext Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(
            LaptopBridgeException(
                message = "Could not get repository workspace status",
                remediationTip = "Check that your laptop bridge is running.",
                endpoint = baseUrl,
                cause = lastException
            )
        )
    }

    suspend fun instructRepoWorkspace(
        workspaceId: String,
        instruction: String,
        timeoutMs: Int = DEFAULT_INSTRUCT_READ_TIMEOUT_MS
    ): Result<RepoWorkspaceInstructResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/repo/workspace/$workspaceId/instruct")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; utf-8")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = timeoutMs // Configurable timeout, default 120s
                }

                val body = JSONObject().apply {
                    put("instruction", instruction)
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val changesObj = json.optJSONObject("changes")
                    val changeSummary = parseChangeSummary(changesObj)

                    return@withContext Result.success(
                        RepoWorkspaceInstructResponse(
                            success = json.optBoolean("success", true),
                            workspace_id = json.optString("workspace_id", workspaceId),
                            stage = json.optString("stage", "COMPLETED"),
                            message = json.optString("message", ""),
                            changes = changeSummary,
                            preview_url = if (json.has("preview_url") && !json.isNull("preview_url")) json.getString("preview_url") else null,
                            error = if (json.has("error") && !json.isNull("error")) json.getString("error") else null
                        )
                    )
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    val msg = extractErrorMessage(errText, responseCode)
                    return@withContext Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(
            LaptopBridgeException(
                message = "Could not execute AI instruction on workspace",
                remediationTip = "Check that your laptop bridge and local Ollama are running.",
                endpoint = baseUrl,
                cause = lastException
            )
        )
    }

    suspend fun getRepoWorkspaceDiff(workspaceId: String): Result<RepoWorkspaceDiffResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/repo/workspace/$workspaceId/diff")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 4000
                    readTimeout = 12000
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val changesObj = json.optJSONObject("changes")
                    val changeSummary = parseChangeSummary(changesObj)

                    return@withContext Result.success(
                        RepoWorkspaceDiffResponse(
                            success = json.optBoolean("success", true),
                            workspace_id = json.optString("workspace_id", workspaceId),
                            baseline_commit = json.optString("baseline_commit", ""),
                            changes = changeSummary,
                            message = if (json.has("message")) json.getString("message") else null
                        )
                    )
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    val msg = extractErrorMessage(errText, responseCode)
                    return@withContext Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(
            LaptopBridgeException(
                message = "Could not fetch git diff from laptop server",
                remediationTip = "Check that your laptop bridge is running.",
                endpoint = baseUrl,
                cause = lastException
            )
        )
    }

    suspend fun startRepoWorkspacePreview(workspaceId: String): Result<RepoWorkspacePreviewResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/repo/workspace/$workspaceId/preview")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 4000
                    readTimeout = 10000
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    return@withContext Result.success(
                        RepoWorkspacePreviewResponse(
                            success = json.optBoolean("success", true),
                            workspace_id = json.optString("workspace_id", workspaceId),
                            status = json.optString("status", "running"),
                            preview_url = if (json.has("preview_url")) json.getString("preview_url") else null,
                            port = if (json.has("port")) json.getInt("port") else null
                        )
                    )
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    val msg = extractErrorMessage(errText, responseCode)
                    return@withContext Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(
            LaptopBridgeException(
                message = "Could not start preview for workspace",
                remediationTip = "Check that your laptop bridge is running.",
                endpoint = baseUrl,
                cause = lastException
            )
        )
    }

    suspend fun stopRepoWorkspacePreview(workspaceId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/repo/workspace/$workspaceId/preview")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 4000
                    readTimeout = 6000
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    return@withContext Result.success(true)
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    val msg = extractErrorMessage(errText, responseCode)
                    return@withContext Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(
            LaptopBridgeException(
                message = "Could not stop preview for workspace",
                remediationTip = "Check that your laptop bridge is running.",
                endpoint = baseUrl,
                cause = lastException
            )
        )
    }

    /**
     * Phase 2D: Fetches pre-publishing change review summary.
     */
    suspend fun getPublishReview(workspaceId: String): Result<PublishReviewResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/repo/workspace/$workspaceId/publish/review")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 4000
                    readTimeout = 12000
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val filesArray = json.optJSONArray("files") ?: JSONArray()
                    val fileList = mutableListOf<FileReviewItem>()
                    for (i in 0 until filesArray.length()) {
                        val fo = filesArray.getJSONObject(i)
                        fileList.add(
                            FileReviewItem(
                                path = fo.optString("path", ""),
                                action = fo.optString("action", "modified"),
                                source = fo.optString("source", "ai_operation"),
                                eligible_for_commit = fo.optBoolean("eligible_for_commit", true),
                                ineligibility_reason = if (fo.has("ineligibility_reason") && !fo.isNull("ineligibility_reason")) fo.getString("ineligibility_reason") else null,
                                staged_in_index = fo.optBoolean("staged_in_index", false),
                                has_collision = fo.optBoolean("has_collision", false)
                            )
                        )
                    }

                    return@withContext Result.success(
                        PublishReviewResponse(
                            success = json.optBoolean("success", true),
                            workspace_id = json.optString("workspace_id", workspaceId),
                            owner = json.optString("owner", ""),
                            repo = json.optString("repo", ""),
                            current_branch = json.optString("current_branch", "main"),
                            remote_name = json.optString("remote_name", "origin"),
                            remote_url = json.optString("remote_url", ""),
                            head_commit = json.optString("head_commit", ""),
                            baseline_commit = json.optString("baseline_commit", ""),
                            files = fileList,
                            diff = json.optString("diff", ""),
                            publish_blocked = json.optBoolean("publish_blocked", false),
                            publish_block_reason = if (json.has("publish_block_reason") && !json.isNull("publish_block_reason")) json.getString("publish_block_reason") else null,
                            operations_count = json.optInt("operations_count", 0),
                            latest_instruction = if (json.has("latest_instruction") && !json.isNull("latest_instruction")) json.getString("latest_instruction") else null,
                            message = if (json.has("message")) json.getString("message") else null
                        )
                    )
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    val msg = extractErrorMessage(errText, responseCode)
                    return@withContext Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(
            LaptopBridgeException(
                message = "Could not fetch publish review summary",
                remediationTip = "Check that your laptop bridge is running and reachable.",
                endpoint = baseUrl,
                cause = lastException
            )
        )
    }

    /**
     * Phase 2D: Executes an authorized, explicit Git commit.
     */
    suspend fun createCommit(
        workspaceId: String,
        commitMessage: String,
        selectedFiles: List<String>,
        authorName: String? = null,
        authorEmail: String? = null
    ): Result<CommitResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/repo/workspace/$workspaceId/commit")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 15000
                }

                val body = JSONObject().apply {
                    put("commit_message", commitMessage)
                    val arr = JSONArray()
                    selectedFiles.forEach { arr.put(it) }
                    put("selected_files", arr)
                    if (authorName != null) put("author_name", authorName)
                    if (authorEmail != null) put("author_email", authorEmail)
                }

                OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val filesArr = json.optJSONArray("committed_files") ?: JSONArray()
                    val filesList = mutableListOf<String>()
                    for (i in 0 until filesArr.length()) {
                        filesList.add(filesArr.getString(i))
                    }

                    return@withContext Result.success(
                        CommitResponse(
                            success = json.optBoolean("success", true),
                            workspace_id = json.optString("workspace_id", workspaceId),
                            commit_hash = json.optString("commit_hash", ""),
                            commit_message = json.optString("commit_message", commitMessage),
                            committed_files = filesList,
                            branch = json.optString("branch", ""),
                            timestamp = json.optString("timestamp", ""),
                            message = if (json.has("message")) json.getString("message") else null
                        )
                    )
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    val msg = extractErrorMessage(errText, responseCode)
                    return@withContext Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(
            LaptopBridgeException(
                message = "Failed to create commit",
                remediationTip = "Check that your laptop bridge is running.",
                endpoint = baseUrl,
                cause = lastException
            )
        )
    }

    /**
     * Phase 2D: Executes a controlled push to the remote repository.
     */
    suspend fun pushToRemote(
        workspaceId: String,
        branch: String? = null,
        remoteName: String? = "origin",
        confirmed: Boolean = true
    ): Result<PushResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/repo/workspace/$workspaceId/push")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 60000
                }

                val body = JSONObject().apply {
                    if (branch != null) put("branch", branch)
                    if (remoteName != null) put("remote_name", remoteName)
                    put("confirmed", confirmed)
                }

                OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)

                    return@withContext Result.success(
                        PushResponse(
                            success = json.optBoolean("success", true),
                            workspace_id = json.optString("workspace_id", workspaceId),
                            pushed_commit = json.optString("pushed_commit", ""),
                            branch = json.optString("branch", ""),
                            remote = json.optString("remote", "origin"),
                            remote_url = json.optString("remote_url", ""),
                            timestamp = json.optString("timestamp", ""),
                            message = if (json.has("message")) json.getString("message") else null
                        )
                    )
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    val msg = extractErrorMessage(errText, responseCode)
                    return@withContext Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(
            LaptopBridgeException(
                message = "Failed to push to remote repository",
                remediationTip = "Check network connection, GitHub authentication, and remote divergence.",
                endpoint = baseUrl,
                cause = lastException
            )
        )
    }

    // =========================================================================
    // ENVIRONMENT SETUP AGENT & IDEA MANAGEMENT API CALLS
    // =========================================================================

    suspend fun detectEnvironment(): Result<EnvironmentDetectionResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/api/environment/detect")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 4000
                    readTimeout = 8000
                }
                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val envJson = json.optJSONObject("environment") ?: JSONObject()
                    val evalsJson = json.optJSONObject("stack_evaluations") ?: JSONObject()

                    val evalsMap = mutableMapOf<String, StackEvaluationItem>()
                    evalsJson.keys().forEach { key ->
                        val itemJson = evalsJson.optJSONObject(key)
                        if (itemJson != null) {
                            val missingList = mutableListOf<String>()
                            val missingArr = itemJson.optJSONArray("missing_tools")
                            if (missingArr != null) {
                                for (i in 0 until missingArr.length()) missingList.add(missingArr.getString(i))
                            }
                            evalsMap[key] = StackEvaluationItem(
                                tech_stack = itemJson.optString("tech_stack", key),
                                supported = itemJson.optBoolean("supported", false),
                                missing_tools = missingList,
                                recommended_environment_type = itemJson.optString("recommended_environment_type", "")
                            )
                        }
                    }

                    fun parseTool(toolJson: JSONObject?): HostToolInfo {
                        if (toolJson == null) return HostToolInfo()
                        return HostToolInfo(
                            installed = toolJson.optBoolean("installed", false),
                            version = if (toolJson.has("version")) toolJson.optString("version") else null,
                            executable_path = if (toolJson.has("executable_path")) toolJson.optString("executable_path") else null,
                            major_version = if (toolJson.has("major_version")) toolJson.optInt("major_version") else null,
                            minor_version = if (toolJson.has("minor_version")) toolJson.optInt("minor_version") else null
                        )
                    }

                    return@withContext Result.success(
                        EnvironmentDetectionResponse(
                            success = json.optBoolean("success", true),
                            environment = HostEnvironmentInfo(
                                os_name = envJson.optString("os_name", "Unknown"),
                                os_release = envJson.optString("os_release", ""),
                                os_version = envJson.optString("os_version", ""),
                                architecture = envJson.optString("architecture", ""),
                                lan_ip = envJson.optString("lan_ip", ""),
                                python = parseTool(envJson.optJSONObject("python")),
                                node = parseTool(envJson.optJSONObject("node")),
                                npm = parseTool(envJson.optJSONObject("npm")),
                                git = parseTool(envJson.optJSONObject("git")),
                                venv_module_available = envJson.optBoolean("venv_module_available", false)
                            ),
                            stack_evaluations = evalsMap
                        )
                    )
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    return@withContext Result.failure(Exception(extractErrorMessage(errText, responseCode)))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(Exception(lastException?.message ?: "Failed to detect environment on laptop"))
    }

    suspend fun planEnvironmentSetup(
        prompt: String,
        techStack: String? = null,
        projectName: String? = null
    ): Result<SetupPlanResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/api/environment/plan")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 12000
                }
                val payload = JSONObject().apply {
                    put("prompt", prompt)
                    if (techStack != null) put("tech_stack", techStack)
                    if (projectName != null) put("project_name", projectName)
                }
                OutputStreamWriter(connection.outputStream).use { it.write(payload.toString()) }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val planJson = json.optJSONObject("plan") ?: JSONObject()
                    val opsJson = planJson.optJSONArray("operations")
                    val opsList = mutableListOf<SetupOperationItem>()
                    if (opsJson != null) {
                        for (i in 0 until opsJson.length()) {
                            val o = opsJson.getJSONObject(i)
                            opsList.add(
                                SetupOperationItem(
                                    op_id = o.optString("op_id", ""),
                                    op_type = o.optString("op_type", ""),
                                    title = o.optString("title", ""),
                                    description = o.optString("description", ""),
                                    status = o.optString("status", "pending")
                                )
                            )
                        }
                    }
                    return@withContext Result.success(
                        SetupPlanResponse(
                            success = json.optBoolean("success", true),
                            plan_id = planJson.optString("plan_id", ""),
                            project_id = planJson.optString("project_id", ""),
                            project_name = planJson.optString("project_name", ""),
                            tech_stack = planJson.optString("tech_stack", "python"),
                            workspace_path = planJson.optString("workspace_path", ""),
                            summary = planJson.optString("summary", ""),
                            operations = opsList,
                            requires_approval = planJson.optBoolean("requires_approval", true),
                            approved = planJson.optBoolean("approved", false),
                            status = planJson.optString("status", "planned")
                        )
                    )
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    return@withContext Result.failure(Exception(extractErrorMessage(errText, responseCode)))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(Exception(lastException?.message ?: "Failed to generate environment plan"))
    }

    suspend fun executeEnvironmentPlan(
        projectId: String,
        approved: Boolean = true,
        prompt: String = ""
    ): Result<SetupPlanResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/api/environment/execute-plan")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 120000 // Up to 120s for dependencies and setup
                }
                val payload = JSONObject().apply {
                    put("project_id", projectId)
                    put("approved", approved)
                    put("prompt", prompt)
                }
                OutputStreamWriter(connection.outputStream).use { it.write(payload.toString()) }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val planJson = json.optJSONObject("plan") ?: JSONObject()
                    return@withContext Result.success(
                        SetupPlanResponse(
                            success = json.optBoolean("success", true),
                            plan_id = planJson.optString("plan_id", ""),
                            project_id = planJson.optString("project_id", projectId),
                            project_name = planJson.optString("project_name", ""),
                            tech_stack = planJson.optString("tech_stack", ""),
                            status = planJson.optString("status", "completed"),
                            preview_url = if (planJson.has("preview_url")) planJson.getString("preview_url") else null,
                            preview_port = if (planJson.has("preview_port")) planJson.getInt("preview_port") else null
                        )
                    )
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    return@withContext Result.failure(Exception(extractErrorMessage(errText, responseCode)))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(Exception(lastException?.message ?: "Failed to execute environment setup plan"))
    }

    suspend fun captureIdea(
        prompt: String,
        title: String? = null,
        techStack: String? = null
    ): Result<IdeaItem> = captureIdeaWithCustomId(prompt, title, techStack, null)

    open suspend fun captureIdeaWithCustomId(
        prompt: String,
        title: String? = null,
        techStack: String? = null,
        ideaId: String? = null
    ): Result<IdeaItem> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/api/ideas/capture")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 8000
                }
                val payload = JSONObject().apply {
                    put("prompt", prompt)
                    if (title != null) put("title", title)
                    if (techStack != null) put("tech_stack", techStack)
                    if (ideaId != null) put("idea_id", ideaId)
                }
                OutputStreamWriter(connection.outputStream).use { it.write(payload.toString()) }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val ideaJson = json.optJSONObject("idea") ?: JSONObject()
                    return@withContext Result.success(
                        IdeaItem(
                            idea_id = ideaJson.optString("idea_id", ideaId ?: ""),
                            title = ideaJson.optString("title", "New Idea"),
                            raw_prompt = ideaJson.optString("raw_prompt", prompt),
                            created_at = ideaJson.optString("created_at", ""),
                            status = ideaJson.optString("status", "captured"),
                            tech_stack = ideaJson.optString("tech_stack", "auto")
                        )
                    )
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    return@withContext Result.failure(Exception(extractErrorMessage(errText, responseCode)))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(Exception(lastException?.message ?: "Failed to capture idea on laptop"))
    }

    open suspend fun updateIdeaOnLaptop(
        ideaId: String,
        prompt: String? = null,
        title: String? = null,
        techStack: String? = null
    ): Result<IdeaItem> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/api/ideas/$ideaId")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 8000
                }
                val payload = JSONObject().apply {
                    if (prompt != null) put("prompt", prompt)
                    if (title != null) put("title", title)
                    if (techStack != null) put("tech_stack", techStack)
                }
                OutputStreamWriter(connection.outputStream).use { it.write(payload.toString()) }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val ideaJson = json.optJSONObject("idea") ?: JSONObject()
                    return@withContext Result.success(
                        IdeaItem(
                            idea_id = ideaJson.optString("idea_id", ideaId),
                            title = ideaJson.optString("title", ""),
                            raw_prompt = ideaJson.optString("raw_prompt", ""),
                            created_at = ideaJson.optString("created_at", ""),
                            status = ideaJson.optString("status", "captured"),
                            tech_stack = ideaJson.optString("tech_stack", "auto")
                        )
                    )
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    return@withContext Result.failure(Exception(extractErrorMessage(errText, responseCode)))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(Exception(lastException?.message ?: "Failed to update idea on laptop"))
    }

    open suspend fun deleteIdeaOnLaptop(ideaId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/api/ideas/$ideaId")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 3000
                    readTimeout = 5000
                }
                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    return@withContext Result.success(true)
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(Exception(lastException?.message ?: "Failed to delete idea on laptop"))
    }

    suspend fun refineIdea(ideaId: String): Result<IdeaItem> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/api/ideas/$ideaId/refine")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 4000
                    readTimeout = 20000
                }
                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val ideaJson = json.optJSONObject("idea") ?: JSONObject()
                    val bpJson = ideaJson.optJSONObject("blueprint")
                    val blueprintItem = if (bpJson != null) {
                        val features = mutableListOf<String>()
                        val featArr = bpJson.optJSONArray("core_features")
                        if (featArr != null) {
                            for (i in 0 until featArr.length()) features.add(featArr.getString(i))
                        }
                        val deps = mutableListOf<String>()
                        val depsArr = bpJson.optJSONArray("key_dependencies")
                        if (depsArr != null) {
                            for (i in 0 until depsArr.length()) deps.add(depsArr.getString(i))
                        }
                        IdeaBlueprintItem(
                            summary = bpJson.optString("summary", ""),
                            target_audience = bpJson.optString("target_audience", ""),
                            core_features = features,
                            suggested_architecture = bpJson.optString("suggested_architecture", ""),
                            recommended_tech_stack = bpJson.optString("recommended_tech_stack", "python"),
                            key_dependencies = deps
                        )
                    } else null

                    return@withContext Result.success(
                        IdeaItem(
                            idea_id = ideaJson.optString("idea_id", ideaId),
                            title = ideaJson.optString("title", ""),
                            raw_prompt = ideaJson.optString("raw_prompt", ""),
                            created_at = ideaJson.optString("created_at", ""),
                            updated_at = ideaJson.optString("updated_at", ""),
                            status = ideaJson.optString("status", "refined"),
                            tech_stack = ideaJson.optString("tech_stack", "auto"),
                            blueprint = blueprintItem,
                            linked_project_id = if (ideaJson.has("linked_project_id")) ideaJson.optString("linked_project_id") else null
                        )
                    )
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    return@withContext Result.failure(Exception(extractErrorMessage(errText, responseCode)))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(Exception(lastException?.message ?: "Failed to refine idea on laptop"))
    }

    suspend fun getEnvironmentStatus(projectId: String): Result<SetupStatusResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/api/environment/status/$projectId")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 3000
                    readTimeout = 5000
                }
                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val completed = mutableListOf<String>()
                    val compArr = json.optJSONArray("completed_operations")
                    if (compArr != null) {
                        for (i in 0 until compArr.length()) completed.add(compArr.getString(i))
                    }
                    return@withContext Result.success(
                        SetupStatusResponse(
                            success = json.optBoolean("success", true),
                            project_id = json.optString("project_id", projectId),
                            status = json.optString("status", "unknown"),
                            progress_pct = json.optDouble("progress_pct", 0.0).toFloat(),
                            current_operation = if (json.has("current_operation")) json.optString("current_operation") else null,
                            completed_operations = completed,
                            server_running = json.optBoolean("server_running", false),
                            server_port = if (json.has("server_port")) json.optInt("server_port") else null,
                            preview_url = if (json.has("preview_url")) json.optString("preview_url") else null,
                            error = if (json.has("error")) json.optString("error") else null
                        )
                    )
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    return@withContext Result.failure(Exception(extractErrorMessage(errText, responseCode)))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(Exception(lastException?.message ?: "Failed to get environment status"))
    }

    suspend fun cancelEnvironmentSetup(projectId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/api/environment/cancel/$projectId")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 3000
                    readTimeout = 5000
                }
                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    return@withContext Result.success(true)
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(Exception(lastException?.message ?: "Failed to cancel environment setup"))
    }

    suspend fun stopDevServer(projectId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            try {
                val endpointUrl = URL("$targetUrl/api/environment/stop-server/$projectId")
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 3000
                    readTimeout = 5000
                }
                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    return@withContext Result.success(true)
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(Exception(lastException?.message ?: "Failed to stop development server"))
    }
}


