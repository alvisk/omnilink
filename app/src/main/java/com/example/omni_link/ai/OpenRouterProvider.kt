package com.example.omni_link.ai

import android.util.Log
import com.example.omni_link.BuildConfig
import com.example.omni_link.data.FocusRegion
import com.example.omni_link.data.ScreenState
import com.example.omni_link.data.Suggestion
import com.example.omni_link.debug.DebugLogManager
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * OpenRouter Cloud Provider for AI inference. Uses OpenRouter API to access multiple AI models
 * including:
 * - OpenAI GPT-4o, GPT-4o-mini
 * - Anthropic Claude 3.5 Sonnet
 * - Google Gemini Pro
 * - Meta Llama 3.1
 * - And many more!
 *
 * API Key is stored in secrets.properties (gitignored) and accessed via BuildConfig.
 */
object OpenRouterProvider {
    private const val TAG = "OpenRouter"

    // OpenRouter API configuration
    private const val OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"

    // Available models on OpenRouter (sorted by quality/speed balance)
    enum class Model(val id: String, val displayName: String, val description: String) {
        // Fast & cheap models - Gemini 2.5 Flash is the new default!
        GEMINI_25_FLASH("google/gemini-2.5-flash", "Gemini 2.5 Flash", "Google's newest & fastest"),
        GPT4O_MINI("openai/gpt-4o-mini", "GPT-4o Mini", "Fast, cheap, great quality"),
        GEMINI_20_FLASH(
                "google/gemini-2.0-flash-001",
                "Gemini 2.0 Flash",
                "Previous gen fast model"
        ),
        CLAUDE_HAIKU("anthropic/claude-3-haiku", "Claude 3 Haiku", "Anthropic's fast model"),

        // Balanced models
        GPT4O("openai/gpt-4o", "GPT-4o", "OpenAI's flagship model"),
        CLAUDE_SONNET(
                "anthropic/claude-3.5-sonnet",
                "Claude 3.5 Sonnet",
                "Best coding & reasoning"
        ),
        GEMINI_PRO("google/gemini-pro-1.5", "Gemini 1.5 Pro", "Google's advanced model"),

        // Open source models
        LLAMA_70B("meta-llama/llama-3.1-70b-instruct", "Llama 3.1 70B", "Meta's large open model"),
        QWEN_72B("qwen/qwen-2.5-72b-instruct", "Qwen 2.5 72B", "Alibaba's powerful model"),
        DEEPSEEK("deepseek/deepseek-chat", "DeepSeek Chat", "DeepSeek's chat model"),

        // Free tier models (may have rate limits)
        LLAMA_8B_FREE("meta-llama/llama-3.1-8b-instruct:free", "Llama 3.1 8B (Free)", "Free tier"),
        GEMMA_7B_FREE("google/gemma-2-9b-it:free", "Gemma 2 9B (Free)", "Free tier");

        companion object {
            fun fromId(id: String): Model? = entries.find { it.id == id }
            fun getDefault(): Model = GEMINI_25_FLASH // Default to Gemini 2.5 Flash
        }
    }

    // Current selected model - default to Gemini 2.5 Flash
    private var currentModel: Model = Model.GEMINI_25_FLASH

    private val gson = Gson()

    // Store last error for debugging
    private var lastError: String? = null

    /** Get the API key from BuildConfig */
    private fun getApiKey(): String? {
        val key = BuildConfig.OPENROUTER_API_KEY
        return if (key.isNotBlank()) key else null
    }

    /** Check if OpenRouter is available (API key configured) */
    fun isAvailable(): Boolean = getApiKey() != null

    /** Get available models */
    fun getAvailableModels(): List<Model> = Model.entries.toList()

    /** Get current model */
    fun getCurrentModel(): Model = currentModel

    /** Set the model to use */
    fun setModel(model: Model) {
        currentModel = model
        Log.d(TAG, "Model changed to: ${model.displayName}")
    }

    /** Set model by ID */
    fun setModelById(modelId: String) {
        Model.fromId(modelId)?.let { setModel(it) }
    }

    /** Test the API connection with a simple prompt */
    suspend fun testConnection(): String {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = getApiKey()
                if (apiKey == null) {
                    return@withContext "❌ No API key configured. Add OPENROUTER_API_KEY to secrets.properties"
                }

                val url = URL(OPENROUTER_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("HTTP-Referer", "https://github.com/omnilink")
                connection.setRequestProperty("X-Title", "OmniLink AI Assistant")
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val testBody = buildRequestBody("Say hello in one word.", currentModel.id)
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                    it.write(testBody)
                }

                val code = connection.responseCode
                if (code == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val text = extractTextFromResponse(response)
                    "✅ Connected to ${currentModel.displayName}! Response: ${text?.take(50) ?: "OK"}..."
                } else {
                    val error = connection.errorStream?.bufferedReader()?.readText()
                    "❌ Error $code: ${error?.take(100)}"
                }
            } catch (e: Exception) {
                "❌ Network error: ${e.message}"
            }
        }
    }

    /**
     * Generate suggestions using OpenRouter cloud. This provides access to a wide variety of AI
     * models.
     */
    fun generateSuggestionsStreaming(
            screenState: ScreenState,
            maxSuggestions: Int = 5,
            focusRegion: FocusRegion? = null
    ): Flow<SuggestionStreamEvent> = callbackFlow {
        try {
            val apiKey = getApiKey()
            if (apiKey == null) {
                trySend(SuggestionStreamEvent.Error("OpenRouter API key not configured"))
                close()
                return@callbackFlow
            }

            Log.d(TAG, "🌐 OpenRouter: Starting inference with ${currentModel.displayName}")
            DebugLogManager.info(
                    TAG,
                    "🌐 OpenRouter Active",
                    "Using ${currentModel.displayName} for cloud inference"
            )

            // Build screen context
            val screenContext =
                    if (focusRegion != null) {
                        screenState.toPromptContextWithFocus(focusRegion)
                    } else {
                        screenState.toPromptContext()
                    }

            // Log the screen context being captured for debugging
            Log.d(
                    TAG,
                    "🌐 Screen Context - Package: ${screenState.packageName}, Elements: ${screenState.flattenElements().size}"
            )
            DebugLogManager.info(
                    TAG,
                    "Screen Context Captured",
                    "App: ${screenState.packageName}\n" +
                            "Activity: ${screenState.activityName ?: "unknown"}\n" +
                            "Elements: ${screenState.flattenElements().size}\n" +
                            "Context length: ${screenContext.length} chars\n" +
                            "Focus region: ${focusRegion?.bounds ?: "Full screen"}"
            )

            // Build the prompt
            val prompt = buildSuggestionPrompt(screenContext, maxSuggestions, focusRegion)

            // Log the full prompt for debugging
            DebugLogManager.prompt(
                    TAG,
                    "OpenRouter Prompt",
                    "Model: ${currentModel.id}\nLength: ${prompt.length} chars\n\n$prompt"
            )

            // Make API call
            val response = withContext(Dispatchers.IO) { callOpenRouterAPI(prompt, apiKey) }

            if (response != null) {
                DebugLogManager.response(TAG, "OpenRouter Response", response)

                // Parse suggestions from response
                val suggestions = parseSuggestionsFromResponse(response, screenState)

                Log.d(TAG, "🌐 OpenRouter complete: ${suggestions.size} suggestions")
                trySend(SuggestionStreamEvent.Complete(suggestions))
            } else {
                val errorDetail = lastError ?: "Unknown error"
                DebugLogManager.error(TAG, "OpenRouter API Failed", errorDetail)
                trySend(SuggestionStreamEvent.Error("OpenRouter: $errorDetail"))
            }

            close()
        } catch (e: Exception) {
            Log.e(TAG, "OpenRouter inference failed", e)
            DebugLogManager.error(TAG, "OpenRouter Failed", e.message ?: "Unknown error")
            trySend(SuggestionStreamEvent.Error("Cloud inference failed: ${e.message}"))
            close()
        }

        awaitClose {}
    }

    /** Build the prompt for suggestions */
    private fun buildSuggestionPrompt(
            screenContext: String,
            maxSuggestions: Int,
            focusRegion: FocusRegion?
    ): String {
        val focusInstruction =
                if (focusRegion != null) {
                    """
IMPORTANT: The user has selected a FOCUS AREA on the screen.
Pay special attention to elements marked as "FOCUSED ELEMENTS" - these are the user's area of interest.
Prioritize suggestions for elements within the focus area over other screen elements.
"""
                } else ""

        return """You are a mobile assistant analyzing a phone screen. Based on the screen content below, suggest $maxSuggestions helpful actions the user might want to take.
$focusInstruction
SCREEN CONTENT:
$screenContext

Return ONLY a valid JSON array. Each object must have these exact fields:
- "title": short action name (e.g. "Call number", "Search web")
- "description": one sentence explanation of what this action does
- "type": one of: dial, sms, search, maps, calendar, alarm, timer, email, copy, share, settings_wifi, settings_bluetooth, open_url, web_search
- "value": the target value (phone number, search query, address, URL, etc.)

Example response:
[{"title":"Call 555-1234","description":"Dial this phone number","type":"dial","value":"555-1234"},{"title":"Search Google","description":"Search for this topic online","type":"web_search","value":"search query"}]

Respond with ONLY the JSON array, no markdown, no explanation:"""
    }

    /** Build the request body for OpenRouter API */
    private fun buildRequestBody(prompt: String, modelId: String): String {
        val escapedPrompt = gson.toJson(prompt)
        return """
{
    "model": "$modelId",
    "messages": [
        {"role": "user", "content": $escapedPrompt}
    ],
    "temperature": 0.1,
    "max_tokens": 500
}
""".trimIndent()
    }

    /** Make the actual API call to OpenRouter */
    private fun callOpenRouterAPI(prompt: String, apiKey: String): String? {
        val url = URL(OPENROUTER_API_URL)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            // Required headers for OpenRouter
            connection.setRequestProperty("HTTP-Referer", "https://github.com/omnilink")
            connection.setRequestProperty("X-Title", "OmniLink AI Assistant")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            // Build request body
            val requestBody = buildRequestBody(prompt, currentModel.id)

            Log.d(TAG, "Sending request to OpenRouter API (${currentModel.id})...")
            Log.d(TAG, "Request body length: ${requestBody.length}")

            // Send request
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            // Read response
            val responseCode = connection.responseCode
            Log.d(TAG, "OpenRouter API response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                Log.d(TAG, "Raw response length: ${response.length}")
                Log.d(TAG, "Raw response preview: ${response.take(500)}")

                val extracted = extractTextFromResponse(response)
                if (extracted == null) {
                    lastError = "Failed to extract text from response: ${response.take(200)}"
                    Log.e(TAG, lastError!!)
                }
                return extracted
            } else {
                val errorResponse =
                        connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                lastError = "API error $responseCode: $errorResponse"
                Log.e(TAG, "OpenRouter API error: $responseCode - $errorResponse")
                DebugLogManager.error(TAG, "OpenRouter API Error", lastError!!)
                return null
            }
        } catch (e: Exception) {
            lastError = "Network error: ${e.message}"
            Log.e(TAG, "OpenRouter API call failed", e)
            throw e
        } finally {
            connection.disconnect()
        }
    }

    /** Extract the text content from OpenRouter API response (OpenAI-compatible format) */
    private fun extractTextFromResponse(response: String): String? {
        return try {
            Log.d(TAG, "Parsing OpenRouter response...")
            val json = JsonParser.parseString(response).asJsonObject

            // Check for error in response
            if (json.has("error")) {
                val error = json.getAsJsonObject("error")
                val message = error.get("message")?.asString ?: "Unknown error"
                lastError = "OpenRouter error: $message"
                Log.e(TAG, lastError!!)
                return null
            }

            val choices = json.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) {
                lastError = "No choices in response"
                Log.e(TAG, lastError!!)
                return null
            }

            val firstChoice = choices[0].asJsonObject
            val message = firstChoice.getAsJsonObject("message")
            if (message == null) {
                lastError = "No message in choice"
                Log.e(TAG, lastError!!)
                return null
            }

            val content = message.get("content")?.asString
            if (content == null) {
                lastError = "No content in message"
                Log.e(TAG, lastError!!)
                return null
            }

            Log.d(TAG, "Extracted text length: ${content.length}")
            Log.d(TAG, "Extracted text preview: ${content.take(200)}")
            return content
        } catch (e: Exception) {
            lastError = "Parse error: ${e.message}"
            Log.e(TAG, "Failed to parse OpenRouter response", e)
            Log.e(TAG, "Response was: ${response.take(500)}")
            null
        }
    }

    /** Get the last error for debugging */
    fun getLastError(): String? = lastError

    /** Parse suggestions from OpenRouter response */
    private fun parseSuggestionsFromResponse(
            response: String,
            screenState: ScreenState
    ): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()

        try {
            // Clean up response - remove markdown code blocks if present
            var cleanResponse = response.trim()
            if (cleanResponse.startsWith("```json")) {
                cleanResponse = cleanResponse.removePrefix("```json").removeSuffix("```").trim()
            } else if (cleanResponse.startsWith("```")) {
                cleanResponse = cleanResponse.removePrefix("```").removeSuffix("```").trim()
            }

            val jsonArray = JsonParser.parseString(cleanResponse).asJsonArray

            for (i in 0 until minOf(jsonArray.size(), 5)) {
                try {
                    val obj = jsonArray[i].asJsonObject
                    val title = obj.get("title")?.asString ?: continue
                    val description = obj.get("description")?.asString ?: ""
                    val type = obj.get("type")?.asString ?: "search"
                    val value = obj.get("value")?.asString ?: ""

                    val action = createActionFromType(type, value)
                    val icon = getIconForType(type)

                    suggestions.add(
                            Suggestion(
                                    id = UUID.randomUUID().toString(),
                                    title = title,
                                    description = description,
                                    action = action,
                                    icon = icon,
                                    priority = 5 - i // Higher priority for earlier suggestions
                            )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse suggestion at index $i", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse OpenRouter suggestions", e)
        }

        return suggestions
    }

    /** Create an AIAction from the type and value */
    private fun createActionFromType(
            type: String,
            value: String
    ): com.example.omni_link.data.AIAction? {
        return when (type.lowercase()) {
            "dial" -> com.example.omni_link.data.AIAction.DialNumber(value)
            "call" -> com.example.omni_link.data.AIAction.CallNumber(value)
            "sms" -> com.example.omni_link.data.AIAction.SendSMS(value, "")
            "search", "web_search" -> com.example.omni_link.data.AIAction.WebSearch(value)
            "maps" -> com.example.omni_link.data.AIAction.OpenMaps(value, navigate = false)
            "navigate" -> com.example.omni_link.data.AIAction.OpenMaps(value, navigate = true)
            "calendar" -> com.example.omni_link.data.AIAction.OpenCalendar(title = value)
            "alarm" -> {
                val parts = value.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
                val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                com.example.omni_link.data.AIAction.SetAlarm(hour, minute)
            }
            "timer" -> {
                val seconds = value.toIntOrNull() ?: 60
                com.example.omni_link.data.AIAction.SetTimer(seconds)
            }
            "email" -> com.example.omni_link.data.AIAction.SendEmail(to = value)
            "copy" -> com.example.omni_link.data.AIAction.CopyToClipboard(value)
            "share" -> com.example.omni_link.data.AIAction.ShareText(value)
            "open_url", "url" -> com.example.omni_link.data.AIAction.OpenURL(value)
            "settings_wifi" ->
                    com.example.omni_link.data.AIAction.OpenSettings(
                            com.example.omni_link.data.AIAction.SettingsSection.WIFI
                    )
            "settings_bluetooth" ->
                    com.example.omni_link.data.AIAction.OpenSettings(
                            com.example.omni_link.data.AIAction.SettingsSection.BLUETOOTH
                    )
            else -> com.example.omni_link.data.AIAction.WebSearch(value)
        }
    }

    /** Get the appropriate icon for the action type */
    private fun getIconForType(type: String): Suggestion.SuggestionIcon {
        return when (type.lowercase()) {
            "dial", "call" -> Suggestion.SuggestionIcon.PHONE
            "sms" -> Suggestion.SuggestionIcon.SMS
            "search", "web_search" -> Suggestion.SuggestionIcon.SEARCH
            "maps", "navigate" -> Suggestion.SuggestionIcon.MAP
            "calendar" -> Suggestion.SuggestionIcon.CALENDAR
            "alarm" -> Suggestion.SuggestionIcon.ALARM
            "timer" -> Suggestion.SuggestionIcon.TIMER
            "email" -> Suggestion.SuggestionIcon.EMAIL
            "copy" -> Suggestion.SuggestionIcon.COPY
            "share" -> Suggestion.SuggestionIcon.SHARE
            "open_url", "url" -> Suggestion.SuggestionIcon.WEB
            "settings_wifi", "settings_bluetooth" -> Suggestion.SuggestionIcon.SETTINGS
            else -> Suggestion.SuggestionIcon.LIGHTBULB
        }
    }
}
