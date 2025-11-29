package com.example.omni_link.ai

import android.content.Context
import android.util.Log
import com.cactus.CactusCompletionParams
import com.cactus.CactusInitParams
import com.cactus.CactusLM
import com.cactus.ChatMessage as CactusChatMessage
import com.example.omni_link.data.AIAction
import com.example.omni_link.data.ActionPlan
import com.example.omni_link.data.FocusRegion
import com.example.omni_link.data.ScreenState
import com.example.omni_link.data.Suggestion
import com.example.omni_link.debug.DebugLogManager
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Cactus SDK integration for on-device LLM inference
 *
 * This provider uses Cactus SDK for high-performance local inference. Supports models like Qwen3,
 * Smol, and Liquid Foundation Models.
 *
 * @see https://cactuscompute.com/docs/kotlin
 * @see https://github.com/cactus-compute/cactus
 */
class CactusLLMProvider(
        private val context: Context,
        private val sharedCactusLM: CactusLM? = null // Optional shared instance from ModelManager
) : LLMProvider {

    companion object {
        private const val TAG = "CactusLLM"

        // ═══════════════════════════════════════════════════════════════════════════
        // 🚀 PERFORMANCE TUNING FOR NOTHING PHONE 3 (Snapdragon 8s Gen 3 + Adreno 735)
        // ═══════════════════════════════════════════════════════════════════════════
        //
        // Hardware specs:
        // - CPU: 1x Cortex-X4 @ 3.0GHz + 4x Cortex-A720 @ 2.8GHz + 3x Cortex-A520 @ 2.0GHz
        // - GPU: Adreno 735 (Cactus SDK handles GPU offload internally)
        // - RAM: 8GB/12GB (largeHeap enabled in manifest)
        //
        // Active optimizations:
        // - Reduced context size (2048) = faster prompt processing
        // - Low temperature (0.3) = faster sampling, more deterministic
        // - Reduced max tokens (512) = faster response generation
        // - Minimal conversation history (2 msgs) = less context to process
        // - arm64-v8a only build = smaller APK, optimized native code
        //
        // Device-side optimizations (user should enable):
        // - Enable "Performance Mode" in Nothing Phone settings
        // - Keep phone plugged in during demo (prevents thermal throttling)
        // - Close other apps to free RAM for model
        // ═══════════════════════════════════════════════════════════════════════════

        // Context size - smaller = faster (2048 is enough for most mobile tasks)
        private const val DEFAULT_CONTEXT_SIZE = 2048

        // Max tokens - for speed, keep responses concise
        private const val DEFAULT_MAX_TOKENS = 512

        // Recommended models for mobile
        val RECOMMENDED_MODELS =
                listOf(
                        // Local server models (fast download from LAN)
                        "local-qwen3-0.6" to "Fast & capable, from local server",
                        "local-gemma3-270m" to "Compact Gemma, from local server",
                        "local-smollm2-360m" to "Ultra-compact, from local server",
                        "local-lfm2-350m" to "Liquid Foundation Model, from local server",
                        // Cloud models
                        "qwen3-0.6" to "Default model, fast and capable",
                        "gemma3-270m" to "Compact Google Gemma model"
                )

        /** Check if a model slug is for a local/self-hosted model */
        fun isLocalModel(slug: String): Boolean = slug.startsWith("local-")

        // System prompt optimized for screen control
        val SYSTEM_PROMPT =
                """
You are Omni, a helpful AI assistant running entirely on the user's device. You can see and interact with any app on their phone through the accessibility service.

## Your Capabilities:
1. READ: See all UI elements on screen (buttons, text, inputs, etc.)
2. CLICK: Tap on any button or interactive element by label
3. TYPE: Enter text into any input field
4. SCROLL: Navigate through scrollable content
5. NAVIGATE: Go back, go home, open apps by name
6. REMEMBER: Store important information across sessions
7. DEVICE ACTIONS: Open calendar, make calls, send SMS, set alarms, navigate maps, and more!

## Response Format:
Always respond in this JSON format:
```json
{
  "thought": "Brief reasoning about what you're doing",
  "response": "Natural language response to show the user",
  "actions": [
    {"type": "click", "target": "button text or description"},
    {"type": "type", "target": "field identifier", "text": "text to enter"},
    {"type": "scroll", "direction": "up|down|left|right"},
    {"type": "back"},
    {"type": "home"},
    {"type": "open_app", "app": "app name"},
    {"type": "wait", "ms": 1000},
    {"type": "open_calendar"},
    {"type": "open_calendar", "title": "Meeting", "description": "Team sync", "location": "Office"},
    {"type": "dial", "phone": "555-1234"},
    {"type": "call", "phone": "555-1234"},
    {"type": "sms", "phone": "555-1234", "message": "Hello!"},
    {"type": "open_url", "url": "https://example.com"},
    {"type": "web_search", "query": "weather today"},
    {"type": "set_alarm", "hour": 7, "minute": 30, "message": "Wake up"},
    {"type": "set_timer", "seconds": 300, "message": "Timer done"},
    {"type": "share", "text": "Check this out!", "subject": "Cool link"},
    {"type": "copy", "text": "text to copy"},
    {"type": "email", "to": "user@example.com", "subject": "Hello", "body": "Hi there!"},
    {"type": "maps", "query": "coffee shops nearby"},
    {"type": "navigate", "query": "123 Main St"},
    {"type": "play_media", "query": "relaxing music"},
    {"type": "camera"},
    {"type": "video"},
    {"type": "settings", "section": "wifi|bluetooth|display|sound|battery|apps|notifications|location|security|accessibility"}
  ],
  "memory": [
    {"key": "user_preference", "value": "dark mode", "category": "preference"}
  ],
  "complete": true
}
```

## Rules:
- Keep responses concise and helpful
- Only perform actions the user explicitly requested
- If you can't find an element, explain what you see instead
- Never share sensitive information visible on screen
- Ask for clarification if the request is ambiguous
- When analyzing screens, describe what you see briefly
- For phone calls and SMS, use dial/sms to let user confirm, use call only when explicitly asked
""".trimIndent()
    }

    // Model state
    private var isModelLoaded = false
    private var currentModelPath: String? = null

    // Cactus LLM instance
    private var cactusLM: CactusLM? = null

    // Mutex to prevent concurrent access to native Cactus library (not thread-safe)
    private val inferenceMutex = Mutex()

    // Flag to indicate if real Cactus inference is available
    private var cactusAvailable = false

    private val gson = Gson()

    override suspend fun isReady(): Boolean = isModelLoaded

    /**
     * Load a model by slug (preferred) or from a GGUF file path (legacy).
     *
     * @param modelPathOrSlug Either a Cactus model slug (e.g., "qwen3-0.6"), a local model (e.g.,
     * "local-model"), or a path to a .gguf file
     */
    override suspend fun loadModel(modelPathOrSlug: String): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Loading model: $modelPathOrSlug")

                    // Determine if this is a slug (no path separators and doesn't end with known
                    // file extensions)
                    // Note: slugs CAN contain dots for version numbers (e.g., "qwen3-0.6")
                    val looksLikeSlug =
                            !modelPathOrSlug.contains('/') &&
                                    !modelPathOrSlug.contains('\\') &&
                                    !modelPathOrSlug.endsWith(".gguf") &&
                                    !modelPathOrSlug.endsWith(".bin")

                    // Check if this is a local model (uses local- prefix)
                    val isLocal = isLocalModel(modelPathOrSlug)

                    // Use shared instance if available (has download state), otherwise create new
                    if (cactusLM == null) {
                        cactusLM = sharedCactusLM ?: CactusLM()
                    }

                    if (looksLikeSlug) {
                        var actualSlug = modelPathOrSlug

                        // Skip catalog verification for local models (they connect to local server)
                        if (!isLocal) {
                            // Query SDK catalog to find the actual model slug
                            try {
                                val models = cactusLM?.getModels() ?: emptyList()
                                Log.d(TAG, "SDK catalog has ${models.size} models:")
                                models.forEach { m ->
                                    Log.d(TAG, "  - ${m.slug} (downloaded=${m.isDownloaded})")
                                }

                                // First try exact match
                                var targetModel = models.firstOrNull { it.slug == modelPathOrSlug }

                                // If not found, try partial match (in case SDK uses different
                                // naming)
                                if (targetModel == null) {
                                    // Try to find a model that contains our slug or vice versa
                                    val baseSlug = modelPathOrSlug.replace("-", "").replace(".", "")
                                    targetModel =
                                            models.firstOrNull { model ->
                                                val modelBase =
                                                        model.slug.replace("-", "").replace(".", "")
                                                modelBase.contains(baseSlug) ||
                                                        baseSlug.contains(modelBase)
                                            }
                                    if (targetModel != null) {
                                        Log.d(
                                                TAG,
                                                "Found partial match: ${targetModel.slug} for requested $modelPathOrSlug"
                                        )
                                        actualSlug = targetModel.slug
                                    }
                                }

                                Log.d(
                                        TAG,
                                        "Target '$modelPathOrSlug' -> actual='$actualSlug' downloaded=${targetModel?.isDownloaded}"
                                )

                                if (targetModel?.isDownloaded != true) {
                                    Log.w(
                                            TAG,
                                            "Model $modelPathOrSlug not found in SDK catalog as downloaded"
                                    )
                                    // Try to find ANY downloaded model as fallback
                                    val fallbackModel = models.firstOrNull { it.isDownloaded }
                                    if (fallbackModel != null) {
                                        Log.d(
                                                TAG,
                                                "Using fallback downloaded model: ${fallbackModel.slug}"
                                        )
                                        actualSlug = fallbackModel.slug
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Could not query SDK catalog: ${e.message}")
                            }
                        } else {
                            Log.d(TAG, "Local model detected - skipping catalog verification")
                        }

                        // Initialize directly by slug (Cactus SDK handles local- prefix internally)
                        // 🚀 PERFORMANCE: Using optimized context size for Nothing Phone 3
                        Log.d(TAG, "Attempting to initialize model by slug: $actualSlug")
                        Log.d(
                                TAG,
                                "🚀 Performance config: contextSize=$DEFAULT_CONTEXT_SIZE (optimized for speed)"
                        )
                        cactusLM?.initializeModel(
                                CactusInitParams(
                                        model = actualSlug,
                                        contextSize = DEFAULT_CONTEXT_SIZE // Reduced for speed
                                )
                        )
                        cactusAvailable = true
                        isModelLoaded = true
                        currentModelPath = actualSlug
                        Log.d(
                                TAG,
                                "Cactus model initialized successfully by slug: $actualSlug${if (isLocal) " (local)" else ""}"
                        )
                        return@withContext Result.success(Unit)
                    }

                    // Legacy path-based loading (derive slug from filename)
                    val modelFile = resolveModelPath(modelPathOrSlug)
                    if (modelFile == null || !modelFile.exists()) {
                        Log.e(TAG, "Model not found at path and slug init failed: $modelPathOrSlug")
                        return@withContext Result.failure(
                                IllegalArgumentException(
                                        "Model not found: $modelPathOrSlug. Make sure the model is downloaded."
                                )
                        )
                    }

                    Log.d(
                            TAG,
                            "Model file found: ${modelFile.absolutePath} (${modelFile.length() / 1024 / 1024}MB)"
                    )

                    return@withContext try {
                        val modelSlug = modelFile.nameWithoutExtension
                        // 🚀 PERFORMANCE: Using optimized context size
                        Log.d(
                                TAG,
                                "🚀 Performance config (legacy): contextSize=$DEFAULT_CONTEXT_SIZE"
                        )
                        cactusLM?.initializeModel(
                                CactusInitParams(
                                        model = modelSlug,
                                        contextSize = DEFAULT_CONTEXT_SIZE // Reduced for speed
                                )
                        )
                        cactusAvailable = true
                        isModelLoaded = true
                        currentModelPath = modelPathOrSlug
                        Log.d(TAG, "Cactus model initialized from file name slug: $modelSlug")
                        Result.success(Unit)
                    } catch (e: Exception) {
                        Log.e(TAG, "Cactus initialization error (legacy): ${e.message}", e)
                        cactusAvailable = false
                        Result.failure(e)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load model: ${e.message}", e)
                    Result.failure(e)
                }
            }

    /** Resolve model path - supports assets, internal storage, and external storage */
    private fun resolveModelPath(modelPath: String): File? {
        // Check if it's an absolute path
        val directFile = File(modelPath)
        if (directFile.exists()) {
            return directFile
        }

        // Check in app's files directory
        val internalFile = File(context.filesDir, modelPath)
        if (internalFile.exists()) {
            return internalFile
        }

        // Check in models subdirectory
        val modelsDir = File(context.filesDir, "models")
        val modelInModelsDir = File(modelsDir, modelPath)
        if (modelInModelsDir.exists()) {
            return modelInModelsDir
        }

        // Check external files directory
        context.getExternalFilesDir(null)?.let { externalDir ->
            val externalFile = File(externalDir, modelPath)
            if (externalFile.exists()) {
                return externalFile
            }
        }

        return null
    }

    override suspend fun unloadModel() {
        try {
            cactusLM?.unload()
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model", e)
        }
        cactusLM = null
        cactusAvailable = false
        isModelLoaded = false
        currentModelPath = null
        Log.d(TAG, "Model unloaded")
    }

    /** Generate a response using the loaded model */
    override suspend fun generateResponse(
            userMessage: String,
            screenState: ScreenState?,
            conversationHistory: List<ChatMessage>,
            memory: List<MemoryItem>
    ): Result<LLMResponse> =
            withContext(Dispatchers.IO) {
                val startTime = System.currentTimeMillis()

                try {
                    if (!isModelLoaded) {
                        return@withContext Result.failure(
                                IllegalStateException("Model not loaded. Call loadModel() first.")
                        )
                    }

                    // Build the prompt with context
                    val messages =
                            buildMessages(userMessage, screenState, conversationHistory, memory)
                    val messagesJson = gson.toJson(messages)

                    Log.d(TAG, "Generating response for: $userMessage")
                    Log.d(TAG, "Context: ${messagesJson.length} chars")

                    // Use Cactus SDK for inference if available, otherwise fall back to smart
                    // response
                    val responseText =
                            if (cactusAvailable && cactusLM != null) {
                                try {
                                    // Build Cactus ChatMessage list
                                    val cactusMessages = mutableListOf<CactusChatMessage>()

                                    // Add system message
                                    cactusMessages.add(
                                            CactusChatMessage(
                                                    content = SYSTEM_PROMPT,
                                                    role = "system"
                                            )
                                    )

                                    // Add conversation history (limited to 2 for maximum speed)
                                    // 🚀 PERFORMANCE: Fewer history = faster context processing
                                    conversationHistory.takeLast(2).forEach { msg ->
                                        cactusMessages.add(
                                                CactusChatMessage(
                                                        content = msg.content,
                                                        role =
                                                                when (msg.role) {
                                                                    ChatMessage.Role.USER -> "user"
                                                                    ChatMessage.Role.ASSISTANT ->
                                                                            "assistant"
                                                                    ChatMessage.Role.SYSTEM ->
                                                                            "system"
                                                                }
                                                )
                                        )
                                    }

                                    // Add current user message with screen context
                                    val userContent = buildString {
                                        if (screenState != null) {
                                            appendLine("[Current Screen]")
                                            appendLine(screenState.toPromptContext())
                                            appendLine()
                                        }
                                        appendLine("[User Request]")
                                        append(userMessage)
                                    }
                                    cactusMessages.add(
                                            CactusChatMessage(content = userContent, role = "user")
                                    )

                                    // Generate completion
                                    // 🚀 PERFORMANCE: Low temperature = faster sampling, more
                                    // deterministic
                                    val result =
                                            cactusLM!!.generateCompletion(
                                                    messages = cactusMessages,
                                                    params =
                                                            CactusCompletionParams(
                                                                    maxTokens = DEFAULT_MAX_TOKENS,
                                                                    temperature =
                                                                            0.3 // Ultra-low for
                                                                    // speed
                                                                    )
                                            )

                                    val responseContent = result?.response
                                    if (!responseContent.isNullOrBlank()) {
                                        Log.d(
                                                TAG,
                                                "Cactus inference success: ${result?.tokensPerSecond ?: 0} tok/s"
                                        )
                                        responseContent
                                    } else {
                                        Log.w(TAG, "Cactus returned empty, using fallback")
                                        generateSmartResponse(userMessage, screenState, memory)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Cactus inference failed, using fallback", e)
                                    generateSmartResponse(userMessage, screenState, memory)
                                }
                            } else {
                                // Fallback to smart response when Cactus not available
                                generateSmartResponse(userMessage, screenState, memory)
                            }

                    val inferenceTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Inference completed in ${inferenceTime}ms")

                    val safeResponseText = responseText ?: ""
                    val actionPlan = parseActions(safeResponseText)

                    Result.success(
                            LLMResponse(
                                    text = safeResponseText,
                                    actions = actionPlan,
                                    memoryUpdates = extractMemoryUpdates(safeResponseText),
                                    inferenceTimeMs = inferenceTime
                            )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Inference failed", e)
                    Result.failure(e)
                }
            }

    /** Generate contextual suggestions based on the current screen state */
    override suspend fun generateSuggestions(
            screenState: ScreenState,
            maxSuggestions: Int
    ): Result<List<Suggestion>> =
            withContext(Dispatchers.IO) {
                try {
                    if (!isModelLoaded) {
                        DebugLogManager.error(
                                TAG,
                                "Model not loaded",
                                "Cannot generate suggestions without a loaded model"
                        )
                        return@withContext Result.failure(
                                IllegalStateException("Model not loaded. Call loadModel() first.")
                        )
                    }

                    val screenContext = screenState.toPromptContext()
                    val elementCount = screenState.flattenElements().size
                    Log.d(TAG, "Generating suggestions for screen: ${screenState.packageName}")

                    DebugLogManager.info(
                            TAG,
                            "Starting suggestion generation",
                            "App: ${screenState.packageName}\n" +
                                    "Elements captured: $elementCount\n" +
                                    "Max suggestions: $maxSuggestions\n" +
                                    "AI available: $cactusAvailable"
                    )

                    // Use AI inference if available, otherwise use smart suggestions
                    val suggestions =
                            if (cactusAvailable && cactusLM != null) {
                                try {
                                    generateAISuggestions(
                                            screenState,
                                            screenContext,
                                            maxSuggestions
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                            TAG,
                                            "AI suggestion generation failed, using smart fallback",
                                            e
                                    )
                                    generateSmartSuggestions(
                                            screenState,
                                            screenContext,
                                            maxSuggestions
                                    )
                                }
                            } else {
                                DebugLogManager.info(
                                        TAG,
                                        "Using smart fallback",
                                        "AI not available, using rule-based suggestions"
                                )
                                generateSmartSuggestions(screenState, screenContext, maxSuggestions)
                            }

                    DebugLogManager.success(
                            TAG,
                            "Generated ${suggestions.size} suggestions",
                            suggestions.joinToString("\n") { "• ${it.title}: ${it.description}" }
                    )
                    Result.success(suggestions)
                } catch (e: Exception) {
                    Log.e(TAG, "Suggestion generation failed", e)
                    DebugLogManager.error(
                            TAG,
                            "Suggestion generation failed",
                            e.message ?: "Unknown error"
                    )
                    Result.failure(e)
                }
            }

    /** Generate suggestions using AI inference - context-aware device actions */
    private suspend fun generateAISuggestions(
            screenState: ScreenState,
            screenContext: String,
            maxSuggestions: Int
    ): List<Suggestion> {
        // Use AI to generate context-aware suggestions based on screen content
        return try {
            generateAISuggestionsFromLLM(screenState, screenContext, maxSuggestions)
        } catch (e: Exception) {
            Log.e(TAG, "AI suggestion generation failed, using smart fallback", e)
            generateSmartSuggestions(screenState, screenContext, maxSuggestions)
        }
    }

    /** Generate AI-powered contextual device action suggestions based on screen content */
    private suspend fun generateAISuggestionsFromLLM(
            screenState: ScreenState,
            screenContext: String,
            maxSuggestions: Int
    ): List<Suggestion> {
        // Simplified, clearer prompt for better model compliance
        // Using explicit JSON format with clear field names
        val suggestionPrompt =
                """You are a mobile assistant. Based on the screen below, suggest $maxSuggestions helpful actions.

SCREEN:
$screenContext

Return ONLY a JSON array. Each object must have these exact fields:
- "title": short action name with emoji (e.g. "📞 Call number")
- "description": one sentence explanation
- "type": one of: dial, sms, search, maps, calendar, alarm, timer, email, camera, copy, share, settings_wifi, settings_bluetooth
- "value": the target (phone number, search query, address, etc.)

For CALENDAR type, include these extra fields if available:
- "event_title": the name of the event
- "event_description": details about the event
- "event_location": where the event takes place
- "event_date": date string (e.g. "2024-12-25" or "tomorrow" or "next Monday")
- "event_time": time string (e.g. "14:30" or "2:30 PM")
- "event_duration_minutes": how long in minutes (default 60)

Example response format:
[{"title":"📞 Call 07483225245","description":"Dial this phone number","type":"dial","value":"07483225245"}]
[{"title":"📅 Add Meeting","description":"Schedule this event","type":"calendar","event_title":"Team Meeting","event_description":"Weekly sync","event_location":"Conference Room A","event_date":"2024-12-20","event_time":"10:00","event_duration_minutes":60}]

JSON array:"""

        DebugLogManager.prompt(
                TAG,
                "Sending AI prompt for suggestions",
                "Prompt length: ${suggestionPrompt.length} chars\n\n$suggestionPrompt"
        )

        val cactusMessages = listOf(CactusChatMessage(content = suggestionPrompt, role = "user"))

        val startTime = System.currentTimeMillis()
        // 🚀 PERFORMANCE: Increased tokens slightly for complete JSON, very low temp for
        // consistency
        // Use mutex to prevent concurrent native library access (causes SIGSEGV)
        val result = inferenceMutex.withLock {
            cactusLM!!.generateCompletion(
                    messages = cactusMessages,
                    params = CactusCompletionParams(maxTokens = 400, temperature = 0.1)
            )
        }
        val inferenceTime = System.currentTimeMillis() - startTime

        val responseContent = result?.response
        DebugLogManager.response(
                TAG,
                "AI response received (${inferenceTime}ms)",
                "Response:\n${responseContent ?: "(empty)"}"
        )

        if (!responseContent.isNullOrBlank()) {
            // Aggressively clean the response
            val cleanedResponse = cleanAIResponse(responseContent)
            DebugLogManager.debug(TAG, "Cleaned response", cleanedResponse)

            val suggestions = parseSuggestionsFromAI(cleanedResponse, screenState)
            if (suggestions.isNotEmpty()) {
                DebugLogManager.debug(
                        TAG,
                        "Parsed ${suggestions.size} suggestions from AI response"
                )
                return suggestions
            } else {
                DebugLogManager.info(
                        TAG,
                        "No valid suggestions parsed, using context-aware fallback"
                )
                // Try to extract context from screen and generate smart suggestions
                return generateContextAwareFallback(screenState, screenContext, maxSuggestions)
            }
        }

        DebugLogManager.info(TAG, "Empty AI response, using fallback")
        return generateSmartSuggestions(screenState, screenContext, maxSuggestions)
    }

    /** Clean AI response by removing thinking tags, markdown, and other artifacts */
    private fun cleanAIResponse(response: String): String {
        var cleaned = response

        // Remove various thinking tag formats (Qwen3, DeepSeek, etc.)
        cleaned = cleaned.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
        cleaned =
                cleaned.replace(
                        Regex("<thinking>[\\s\\S]*?</thinking>", RegexOption.IGNORE_CASE),
                        ""
                )
        cleaned = cleaned.replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("```\\s*$", RegexOption.MULTILINE), "")
        cleaned = cleaned.replace(Regex("^```\\s*", RegexOption.MULTILINE), "")

        // Remove any text before the first [ (common for models that add explanations)
        val arrayStart = cleaned.indexOf('[')
        if (arrayStart > 0) {
            cleaned = cleaned.substring(arrayStart)
        }

        // Remove any text after the last complete ] that closes the array
        val arrayEnd = cleaned.lastIndexOf(']')
        if (arrayEnd > 0) {
            cleaned = cleaned.substring(0, arrayEnd + 1)
        }

        return cleaned.trim()
    }

    /** Generate context-aware suggestions when AI parsing fails */
    private fun generateContextAwareFallback(
            screenState: ScreenState,
            screenContext: String,
            maxSuggestions: Int
    ): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        val contextLower = screenContext.lowercase()

        // Extract phone numbers from screen
        val phonePattern =
                Regex("""(?:\+?\d{1,3}[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}|\d{10,11}""")
        phonePattern.findAll(screenContext).take(2).forEach { match ->
            val phone = match.value.replace(Regex("[\\s\\-\\(\\)]"), "")
            suggestions.add(
                    createSuggestion(
                            "📞 Call $phone",
                            "Dial this phone number",
                            Suggestion.SuggestionIcon.PHONE,
                            AIAction.DialNumber(phone)
                    )
            )
            suggestions.add(
                    createSuggestion(
                            "💬 Text $phone",
                            "Send SMS to this number",
                            Suggestion.SuggestionIcon.SMS,
                            AIAction.SendSMS(phone, "")
                    )
            )
        }

        // Extract email addresses
        val emailPattern = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")
        emailPattern.findAll(screenContext).take(1).forEach { match ->
            suggestions.add(
                    createSuggestion(
                            "✉️ Email ${match.value}",
                            "Send an email",
                            Suggestion.SuggestionIcon.EMAIL,
                            AIAction.SendEmail(match.value, null, null)
                    )
            )
        }

        // Extract URLs
        val urlPattern = Regex("""https?://[^\s<>"{}|\\^`\[\]]+""", RegexOption.IGNORE_CASE)
        urlPattern.findAll(screenContext).take(1).forEach { match ->
            suggestions.add(
                    createSuggestion(
                            "🌐 Open link",
                            "Open this URL",
                            Suggestion.SuggestionIcon.WEB,
                            AIAction.OpenURL(match.value)
                    )
            )
        }

        // Extract addresses/locations (simple heuristic)
        if (contextLower.contains("address") ||
                        contextLower.contains("location") ||
                        contextLower.contains("street") ||
                        contextLower.contains("ave") ||
                        contextLower.contains("blvd") ||
                        contextLower.contains("road")
        ) {
            suggestions.add(
                    createSuggestion(
                            "🗺️ Open Maps",
                            "View location on maps",
                            Suggestion.SuggestionIcon.MAP,
                            AIAction.OpenMaps(query = "")
                    )
            )
        }

        // Browser-specific suggestions
        val packageName = screenState.packageName?.lowercase() ?: ""
        if (packageName.contains("chrome") || packageName.contains("browser")) {
            // Check if there's a search query visible
            if (contextLower.contains("search") || contextLower.contains("google")) {
                suggestions.add(
                        createSuggestion(
                                "🔍 New search",
                                "Search the web",
                                Suggestion.SuggestionIcon.SEARCH,
                                AIAction.WebSearch("")
                        )
                )
            }
            suggestions.add(
                    createSuggestion(
                            "📋 Copy page URL",
                            "Copy current page link",
                            Suggestion.SuggestionIcon.COPY,
                            AIAction.CopyToClipboard("")
                    )
            )
            suggestions.add(
                    createSuggestion(
                            "📤 Share page",
                            "Share this page",
                            Suggestion.SuggestionIcon.SHARE,
                            AIAction.ShareText("")
                    )
            )
        }

        // If we still don't have enough, add smart suggestions
        if (suggestions.size < maxSuggestions) {
            val smartSuggestions =
                    generateSmartSuggestions(
                            screenState,
                            screenContext,
                            maxSuggestions - suggestions.size
                    )
            suggestions.addAll(smartSuggestions)
        }

        return suggestions.take(maxSuggestions)
    }

    /** Parse AI-generated suggestions from response */
    private fun parseSuggestionsFromAI(
            response: String,
            screenState: ScreenState
    ): List<Suggestion> {
        return try {
            val jsonString = extractJsonArrayFromResponse(response)
            if (jsonString == null) {
                DebugLogManager.error(TAG, "No valid JSON array found in response", response)
                return emptyList()
            }

            val jsonArray = JsonParser.parseString(jsonString).asJsonArray
            DebugLogManager.debug(TAG, "Parsing ${jsonArray.size()} suggestion objects")

            jsonArray.mapIndexedNotNull { index, element ->
                try {
                    val obj = element.asJsonObject

                    // Validate required fields exist and are non-empty
                    val title = obj.get("title")?.asString?.takeIf { it.isNotBlank() }
                    val type = obj.get("type")?.asString?.takeIf { it.isNotBlank() }

                    // Reject malformed objects (e.g. {"TextView": ""})
                    if (title == null || type == null) {
                        DebugLogManager.debug(
                                TAG,
                                "Skipping malformed object at index $index: missing title or type"
                        )
                        return@mapIndexedNotNull null
                    }

                    // Validate type is a known action type
                    val knownTypes =
                            setOf(
                                    "click",
                                    "type",
                                    "scroll",
                                    "navigate",
                                    "back",
                                    "app",
                                    "search",
                                    "share",
                                    "copy",
                                    "info",
                                    "calendar",
                                    "dial",
                                    "phone",
                                    "call",
                                    "sms",
                                    "message",
                                    "text",
                                    "alarm",
                                    "timer",
                                    "email",
                                    "maps",
                                    "map",
                                    "navigation",
                                    "camera",
                                    "photo",
                                    "video",
                                    "music",
                                    "media",
                                    "play",
                                    "url",
                                    "web",
                                    "browser",
                                    "settings",
                                    "settings_wifi",
                                    "settings_bluetooth",
                                    "settings_display",
                                    "settings_sound",
                                    "settings_battery"
                            )
                    if (type.lowercase() !in knownTypes) {
                        DebugLogManager.debug(
                                TAG,
                                "Skipping object at index $index: unknown type '$type'"
                        )
                        return@mapIndexedNotNull null
                    }

                    val description = obj.get("description")?.asString ?: ""
                    // Support both "value" (from prompt) and "target" (legacy) field names
                    val value = obj.get("value")?.asString ?: obj.get("target")?.asString

                    // For calendar type, extract full event details
                    val action =
                            if (type.lowercase() == "calendar") {
                                createCalendarAction(obj)
                            } else {
                                createActionFromType(type, value, screenState)
                            }

                    Suggestion(
                            id = UUID.randomUUID().toString(),
                            title = title,
                            description = description,
                            action = action,
                            icon = getIconFromType(type),
                            priority = jsonArray.size() - index
                    )
                } catch (e: Exception) {
                    DebugLogManager.error(
                            TAG,
                            "Failed to parse suggestion at index $index",
                            e.message
                    )
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AI suggestions", e)
            DebugLogManager.error(
                    TAG,
                    "JSON parse error",
                    "${e.message}\n\nRaw response:\n$response"
            )
            emptyList()
        }
    }

    private fun extractJsonArrayFromResponse(response: String): String? {
        val trimmed = response.trim()

        // Find JSON array
        val arrayStart = trimmed.indexOf('[')
        if (arrayStart >= 0) {
            var bracketCount = 0
            var endIndex = -1
            var lastCompleteObjectEnd = -1 // Track last complete object for recovery
            var inString = false
            var escaped = false

            for (i in arrayStart until trimmed.length) {
                val c = trimmed[i]

                // Handle string escaping
                if (escaped) {
                    escaped = false
                    continue
                }
                if (c == '\\') {
                    escaped = true
                    continue
                }
                if (c == '"') {
                    inString = !inString
                    continue
                }
                if (inString) continue

                when (c) {
                    '[' -> bracketCount++
                    ']' -> {
                        bracketCount--
                        if (bracketCount == 0) {
                            endIndex = i
                            break
                        }
                    }
                    '}' -> {
                        // Track end of complete objects at array level
                        if (bracketCount == 1) {
                            lastCompleteObjectEnd = i
                        }
                    }
                }
            }

            if (endIndex > arrayStart) {
                return trimmed.substring(arrayStart, endIndex + 1)
            }

            // JSON was truncated - try to salvage complete objects
            if (lastCompleteObjectEnd > arrayStart) {
                val salvaged = trimmed.substring(arrayStart, lastCompleteObjectEnd + 1) + "]"
                DebugLogManager.info(
                        TAG,
                        "JSON truncated - salvaged partial response",
                        "Original length: ${trimmed.length}, salvaged: $salvaged"
                )
                return salvaged
            }

            DebugLogManager.error(
                    TAG,
                    "JSON array incomplete - no closing bracket found",
                    "Response was likely cut off by token limit. Raw response:\n$trimmed"
            )
        }
        return null
    }

    /**
     * Create a calendar action with full event details from JSON object Parses event_title,
     * event_description, event_location, event_date, event_time, event_duration_minutes
     */
    private fun createCalendarAction(jsonObj: com.google.gson.JsonObject): AIAction {
        val eventTitle =
                jsonObj.get("event_title")?.asString
                        ?: jsonObj.get("value")?.asString ?: jsonObj.get("target")?.asString
        val eventDescription = jsonObj.get("event_description")?.asString
        val eventLocation = jsonObj.get("event_location")?.asString
        val eventDate = jsonObj.get("event_date")?.asString
        val eventTime = jsonObj.get("event_time")?.asString
        val durationMinutes = jsonObj.get("event_duration_minutes")?.asInt ?: 60

        // Parse date and time into milliseconds
        val startTimeMillis = parseEventDateTime(eventDate, eventTime)
        val endTimeMillis = startTimeMillis?.let { it + (durationMinutes * 60 * 1000L) }

        return AIAction.OpenCalendar(
                title = eventTitle,
                description = eventDescription,
                location = eventLocation,
                startTime = startTimeMillis,
                endTime = endTimeMillis
        )
    }

    /**
     * Parse date and time strings into milliseconds since epoch Supports formats like "2024-12-25",
     * "tomorrow", "next Monday", "14:30", "2:30 PM"
     */
    private fun parseEventDateTime(dateStr: String?, timeStr: String?): Long? {
        if (dateStr == null && timeStr == null) return null

        val calendar = java.util.Calendar.getInstance()

        // Parse date if provided
        if (dateStr != null) {
            val dateLower = dateStr.lowercase().trim()
            when {
                dateLower == "today" -> {
                    /* calendar is already today */
                }
                dateLower == "tomorrow" -> calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                dateLower.startsWith("next ") -> {
                    val dayName = dateLower.removePrefix("next ").trim()
                    val targetDay = parseDayOfWeek(dayName)
                    if (targetDay != null) {
                        val currentDay = calendar.get(java.util.Calendar.DAY_OF_WEEK)
                        var daysToAdd = targetDay - currentDay
                        if (daysToAdd <= 0) daysToAdd += 7
                        calendar.add(java.util.Calendar.DAY_OF_YEAR, daysToAdd)
                    }
                }
                dateLower.matches(Regex("""\d{4}-\d{2}-\d{2}""")) -> {
                    // ISO format: 2024-12-25
                    try {
                        val parts = dateLower.split("-")
                        calendar.set(java.util.Calendar.YEAR, parts[0].toInt())
                        calendar.set(java.util.Calendar.MONTH, parts[1].toInt() - 1)
                        calendar.set(java.util.Calendar.DAY_OF_MONTH, parts[2].toInt())
                    } catch (e: Exception) {
                        /* keep current date */
                    }
                }
                dateLower.matches(Regex("""\d{1,2}/\d{1,2}/\d{2,4}""")) -> {
                    // US format: 12/25/2024 or 12/25/24
                    try {
                        val parts = dateLower.split("/")
                        calendar.set(java.util.Calendar.MONTH, parts[0].toInt() - 1)
                        calendar.set(java.util.Calendar.DAY_OF_MONTH, parts[1].toInt())
                        val year = parts[2].toInt()
                        calendar.set(java.util.Calendar.YEAR, if (year < 100) 2000 + year else year)
                    } catch (e: Exception) {
                        /* keep current date */
                    }
                }
                dateLower.matches(
                        Regex("""(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec).*""")
                ) -> {
                    // Month name format: "Dec 25" or "December 25, 2024"
                    try {
                        val monthMatch =
                                Regex("""(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)""")
                                        .find(dateLower)
                        val dayMatch = Regex("""\d{1,2}""").find(dateLower)
                        val yearMatch = Regex("""\d{4}""").find(dateLower)

                        if (monthMatch != null && dayMatch != null) {
                            val monthNum =
                                    when (monthMatch.value) {
                                        "jan" -> 0
                                        "feb" -> 1
                                        "mar" -> 2
                                        "apr" -> 3
                                        "may" -> 4
                                        "jun" -> 5
                                        "jul" -> 6
                                        "aug" -> 7
                                        "sep" -> 8
                                        "oct" -> 9
                                        "nov" -> 10
                                        "dec" -> 11
                                        else -> 0
                                    }
                            calendar.set(java.util.Calendar.MONTH, monthNum)
                            calendar.set(java.util.Calendar.DAY_OF_MONTH, dayMatch.value.toInt())
                            if (yearMatch != null) {
                                calendar.set(java.util.Calendar.YEAR, yearMatch.value.toInt())
                            }
                        }
                    } catch (e: Exception) {
                        /* keep current date */
                    }
                }
            }
        }

        // Parse time if provided
        if (timeStr != null) {
            val timeLower = timeStr.lowercase().trim()
            try {
                when {
                    timeLower.matches(
                            Regex("""\d{1,2}:\d{2}\s*(am|pm)?""", RegexOption.IGNORE_CASE)
                    ) -> {
                        val isPM = timeLower.contains("pm", ignoreCase = true)
                        val isAM = timeLower.contains("am", ignoreCase = true)
                        val timeClean = timeLower.replace(Regex("[apmAPM\\s]"), "")
                        val parts = timeClean.split(":")
                        var hour = parts[0].toInt()
                        val minute = parts[1].toInt()

                        // Handle 12-hour format
                        if (isPM && hour < 12) hour += 12
                        if (isAM && hour == 12) hour = 0

                        calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
                        calendar.set(java.util.Calendar.MINUTE, minute)
                        calendar.set(java.util.Calendar.SECOND, 0)
                        calendar.set(java.util.Calendar.MILLISECOND, 0)
                    }
                }
            } catch (e: Exception) {
                /* keep current time */
            }
        } else {
            // Default to 9:00 AM if only date is provided
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 9)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
        }

        return calendar.timeInMillis
    }

    /** Parse day of week name to Calendar constant */
    private fun parseDayOfWeek(dayName: String): Int? {
        return when (dayName.lowercase()) {
            "sunday", "sun" -> java.util.Calendar.SUNDAY
            "monday", "mon" -> java.util.Calendar.MONDAY
            "tuesday", "tue", "tues" -> java.util.Calendar.TUESDAY
            "wednesday", "wed" -> java.util.Calendar.WEDNESDAY
            "thursday", "thu", "thurs" -> java.util.Calendar.THURSDAY
            "friday", "fri" -> java.util.Calendar.FRIDAY
            "saturday", "sat" -> java.util.Calendar.SATURDAY
            else -> null
        }
    }

    /** Data class to hold extracted event details from screen content */
    private data class ExtractedEventDetails(
            val title: String? = null,
            val description: String? = null,
            val location: String? = null,
            val startTime: Long? = null,
            val endTime: Long? = null
    )

    /**
     * Extract potential event details from screen content using heuristics Looks for patterns like
     * titles, dates, times, locations
     */
    private fun extractEventDetailsFromScreen(screenContext: String): ExtractedEventDetails {
        var title: String? = null
        var description: String? = null
        var location: String? = null
        var dateStr: String? = null
        var timeStr: String? = null

        val lines = screenContext.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Look for potential event title (first non-date/time line with reasonable length)
        for (line in lines) {
            val lineLower = line.lowercase()
            // Skip if line looks like a date, time, or UI element
            if (lineLower.matches(Regex(""".*\d{1,2}[:/]\d{2}.*""")) && line.length < 10) continue
            if (lineLower.contains("button") ||
                            lineLower.contains("click") ||
                            lineLower.contains("tap")
            )
                    continue
            if (line.length in 3..60 && !lineLower.startsWith("at ") && !lineLower.startsWith("on ")
            ) {
                title = line
                break
            }
        }

        // Extract date patterns
        val datePatterns =
                listOf(
                        Regex("""\b(\d{4}-\d{2}-\d{2})\b"""), // ISO: 2024-12-25
                        Regex("""\b(\d{1,2}/\d{1,2}/\d{2,4})\b"""), // US: 12/25/2024
                        Regex("""\b(tomorrow|today)\b""", RegexOption.IGNORE_CASE),
                        Regex(
                                """\b(next\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday))\b""",
                                RegexOption.IGNORE_CASE
                        ),
                        Regex(
                                """\b((?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+\d{1,2}(?:st|nd|rd|th)?(?:,?\s+\d{4})?)\b""",
                                RegexOption.IGNORE_CASE
                        )
                )

        for (pattern in datePatterns) {
            val match = pattern.find(screenContext)
            if (match != null) {
                dateStr = match.value
                break
            }
        }

        // Extract time patterns
        val timePatterns =
                listOf(
                        Regex("""\b(\d{1,2}:\d{2}\s*(?:am|pm)?)\b""", RegexOption.IGNORE_CASE),
                        Regex("""\b(\d{1,2}\s*(?:am|pm))\b""", RegexOption.IGNORE_CASE)
                )

        for (pattern in timePatterns) {
            val match = pattern.find(screenContext)
            if (match != null) {
                timeStr = match.value
                break
            }
        }

        // Extract location (look for common location indicators)
        val locationPatterns =
                listOf(
                        Regex(
                                """(?:at|location:|venue:|where:)\s*([^,\n]{5,50})""",
                                RegexOption.IGNORE_CASE
                        ),
                        Regex(
                                """(?:room|conference|office|building)\s+\w+""",
                                RegexOption.IGNORE_CASE
                        ),
                        Regex(
                                """\d+\s+\w+\s+(?:street|st|avenue|ave|road|rd|blvd|drive|dr)""",
                                RegexOption.IGNORE_CASE
                        )
                )

        for (pattern in locationPatterns) {
            val match = pattern.find(screenContext)
            if (match != null) {
                location = match.groupValues.getOrNull(1) ?: match.value
                location = location.trim()
                break
            }
        }

        // Parse the extracted date/time into milliseconds
        val startTimeMillis = parseEventDateTime(dateStr, timeStr)
        val endTimeMillis =
                startTimeMillis?.let { it + (60 * 60 * 1000L) } // Default 1 hour duration

        // Use remaining relevant text as description
        if (title != null) {
            val remainingLines =
                    lines
                            .filter {
                                it != title &&
                                        it.length > 10 &&
                                        !it.contains(dateStr ?: "____") &&
                                        !it.contains(timeStr ?: "____") &&
                                        !it.contains(location ?: "____")
                            }
                            .take(2)
            if (remainingLines.isNotEmpty()) {
                description = remainingLines.joinToString(". ")
            }
        }

        return ExtractedEventDetails(
                title = title,
                description = description,
                location = location,
                startTime = startTimeMillis,
                endTime = endTimeMillis
        )
    }

    private fun createActionFromType(
            type: String,
            target: String?,
            screenState: ScreenState
    ): AIAction? {
        return when (type.lowercase()) {
            // UI interaction actions
            "click" -> target?.let { AIAction.Click(it) }
            "type" -> target?.let { AIAction.Type(it, "") }
            "scroll" -> AIAction.Scroll(AIAction.ScrollDirection.DOWN)
            "navigate", "back" -> AIAction.Back
            "app" -> target?.let { AIAction.OpenApp(it) }
            // Device intent actions
            "calendar" -> AIAction.OpenCalendar(title = target)
            "dial", "phone" -> AIAction.DialNumber(target ?: "")
            "call" -> AIAction.CallNumber(target ?: "")
            "sms", "message", "text" -> AIAction.SendSMS(target ?: "", "")
            "alarm" -> AIAction.SetAlarm(8, 0, target)
            "timer" -> AIAction.SetTimer(target?.toIntOrNull() ?: 60, null)
            "email" -> AIAction.SendEmail(target ?: "", null, null)
            "maps", "map" -> AIAction.OpenMaps(query = target, navigate = false)
            "navigation" -> AIAction.OpenMaps(query = target, navigate = true)
            "camera", "photo" -> AIAction.CaptureMedia(video = false)
            "video" -> AIAction.CaptureMedia(video = true)
            "music", "media", "play" -> AIAction.PlayMedia(target ?: "")
            "search" -> AIAction.WebSearch(target ?: "")
            "url", "web", "browser" -> AIAction.OpenURL(target ?: "https://google.com")
            "share" -> AIAction.ShareText(target ?: "")
            "copy" -> AIAction.CopyToClipboard(target ?: "")
            "settings" -> AIAction.OpenSettings(AIAction.SettingsSection.MAIN)
            "settings_wifi" -> AIAction.OpenSettings(AIAction.SettingsSection.WIFI)
            "settings_bluetooth" -> AIAction.OpenSettings(AIAction.SettingsSection.BLUETOOTH)
            "settings_display" -> AIAction.OpenSettings(AIAction.SettingsSection.DISPLAY)
            "settings_sound" -> AIAction.OpenSettings(AIAction.SettingsSection.SOUND)
            "settings_battery" -> AIAction.OpenSettings(AIAction.SettingsSection.BATTERY)
            else -> null
        }
    }

    private fun getIconFromType(type: String): Suggestion.SuggestionIcon {
        return when (type.lowercase()) {
            // UI interaction icons
            "click" -> Suggestion.SuggestionIcon.CLICK
            "type" -> Suggestion.SuggestionIcon.TYPE
            "scroll" -> Suggestion.SuggestionIcon.SCROLL
            "navigate", "back" -> Suggestion.SuggestionIcon.NAVIGATE
            "app" -> Suggestion.SuggestionIcon.APP
            "search" -> Suggestion.SuggestionIcon.SEARCH
            "share" -> Suggestion.SuggestionIcon.SHARE
            "copy" -> Suggestion.SuggestionIcon.COPY
            "info" -> Suggestion.SuggestionIcon.INFO
            // Device intent icons
            "calendar" -> Suggestion.SuggestionIcon.CALENDAR
            "dial", "phone", "call" -> Suggestion.SuggestionIcon.PHONE
            "sms", "message", "text" -> Suggestion.SuggestionIcon.SMS
            "alarm" -> Suggestion.SuggestionIcon.ALARM
            "timer" -> Suggestion.SuggestionIcon.TIMER
            "email" -> Suggestion.SuggestionIcon.EMAIL
            "maps", "map", "navigation" -> Suggestion.SuggestionIcon.MAP
            "camera", "photo" -> Suggestion.SuggestionIcon.CAMERA
            "video" -> Suggestion.SuggestionIcon.VIDEO
            "music", "media", "play" -> Suggestion.SuggestionIcon.MUSIC
            "settings",
            "settings_wifi",
            "settings_bluetooth",
            "settings_display",
            "settings_sound",
            "settings_battery" -> Suggestion.SuggestionIcon.SETTINGS
            "url", "web", "browser" -> Suggestion.SuggestionIcon.WEB
            else -> Suggestion.SuggestionIcon.LIGHTBULB
        }
    }

    /** Generate smart suggestions based on screen analysis - context-aware device actions */
    private fun generateSmartSuggestions(
            screenState: ScreenState,
            screenContext: String,
            maxSuggestions: Int
    ): List<Suggestion> {
        val contextLower = screenContext.lowercase()
        val packageName = screenState.packageName?.lowercase() ?: ""
        val suggestions = mutableListOf<Suggestion>()

        // Detect context and add relevant suggestions first
        val isCalendarContext =
                packageName.contains("calendar") ||
                        contextLower.contains("event") ||
                        contextLower.contains("schedule") ||
                        contextLower.contains("meeting") ||
                        contextLower.contains("appointment")

        val isPhoneContext =
                packageName.contains("dialer") ||
                        packageName.contains("phone") ||
                        contextLower.contains("call") ||
                        contextLower.contains("phone number") ||
                        contextLower.contains("contact")

        val isMessagingContext =
                packageName.contains("message") ||
                        packageName.contains("sms") ||
                        contextLower.contains("text") ||
                        contextLower.contains("message") ||
                        contextLower.contains("chat")

        val isEmailContext =
                packageName.contains("mail") ||
                        packageName.contains("gmail") ||
                        contextLower.contains("email") ||
                        contextLower.contains("inbox") ||
                        contextLower.contains("compose")

        val isMapContext =
                packageName.contains("map") ||
                        packageName.contains("navigation") ||
                        contextLower.contains("direction") ||
                        contextLower.contains("location") ||
                        contextLower.contains("address") ||
                        contextLower.contains("navigate")

        val isBrowserContext =
                packageName.contains("browser") ||
                        packageName.contains("chrome") ||
                        contextLower.contains("search") ||
                        contextLower.contains("url") ||
                        contextLower.contains("website")

        val isMediaContext =
                packageName.contains("music") ||
                        packageName.contains("spotify") ||
                        packageName.contains("youtube") ||
                        contextLower.contains("play") ||
                        contextLower.contains("song") ||
                        contextLower.contains("video")

        val isCameraContext =
                packageName.contains("camera") ||
                        contextLower.contains("photo") ||
                        contextLower.contains("picture") ||
                        contextLower.contains("capture")

        val isSettingsContext =
                packageName.contains("settings") ||
                        contextLower.contains("wifi") ||
                        contextLower.contains("bluetooth") ||
                        contextLower.contains("brightness") ||
                        contextLower.contains("battery")

        // Add context-relevant suggestions first
        if (isCalendarContext) {
            // Try to extract event details from screen content
            val eventDetails = extractEventDetailsFromScreen(screenContext)
            suggestions.add(
                    createSuggestion(
                            "📅 Create Event" + (eventDetails.title?.let { ": $it" } ?: ""),
                            eventDetails.description ?: "Add new calendar event",
                            Suggestion.SuggestionIcon.CALENDAR,
                            AIAction.OpenCalendar(
                                    title = eventDetails.title,
                                    description = eventDetails.description,
                                    location = eventDetails.location,
                                    startTime = eventDetails.startTime,
                                    endTime = eventDetails.endTime
                            )
                    )
            )
        }
        if (isPhoneContext) {
            suggestions.add(
                    createSuggestion(
                            "📞 Make a Call",
                            "Open dialer",
                            Suggestion.SuggestionIcon.PHONE,
                            AIAction.DialNumber("")
                    )
            )
        }
        if (isMessagingContext) {
            suggestions.add(
                    createSuggestion(
                            "💬 Send Message",
                            "Compose a text",
                            Suggestion.SuggestionIcon.SMS,
                            AIAction.SendSMS("", "")
                    )
            )
        }
        if (isEmailContext) {
            suggestions.add(
                    createSuggestion(
                            "✉️ Compose Email",
                            "Write new email",
                            Suggestion.SuggestionIcon.EMAIL,
                            AIAction.SendEmail("", null, null)
                    )
            )
        }
        if (isMapContext) {
            suggestions.add(
                    createSuggestion(
                            "🧭 Get Directions",
                            "Start navigation",
                            Suggestion.SuggestionIcon.MAP,
                            AIAction.OpenMaps(navigate = true)
                    )
            )
        }
        if (isBrowserContext) {
            suggestions.add(
                    createSuggestion(
                            "🔍 Web Search",
                            "Search the internet",
                            Suggestion.SuggestionIcon.SEARCH,
                            AIAction.WebSearch("")
                    )
            )
        }
        if (isMediaContext) {
            suggestions.add(
                    createSuggestion(
                            "🎵 Play Music",
                            "Play some tunes",
                            Suggestion.SuggestionIcon.MUSIC,
                            AIAction.PlayMedia("")
                    )
            )
        }
        if (isCameraContext) {
            suggestions.add(
                    createSuggestion(
                            "📷 Take Photo",
                            "Capture a moment",
                            Suggestion.SuggestionIcon.CAMERA,
                            AIAction.CaptureMedia(video = false)
                    )
            )
        }
        if (isSettingsContext) {
            suggestions.add(
                    createSuggestion(
                            "⚙️ Open Settings",
                            "Configure device",
                            Suggestion.SuggestionIcon.SETTINGS,
                            AIAction.OpenSettings()
                    )
            )
        }

        // Fill remaining slots with general suggestions
        val allSuggestions = generateDeviceActionSuggestions(20)
        for (suggestion in allSuggestions) {
            if (suggestions.size >= maxSuggestions) break
            if (suggestions.none { it.title == suggestion.title }) {
                suggestions.add(suggestion)
            }
        }

        return suggestions.take(maxSuggestions)
    }

    /**
     * Generate device intent action suggestions - these are the primary suggestions shown in
     * overlay
     */
    private fun generateDeviceActionSuggestions(maxSuggestions: Int): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()

        // Calendar
        suggestions.add(
                createSuggestion(
                        "📅 Open Calendar",
                        "View your calendar and events",
                        Suggestion.SuggestionIcon.CALENDAR,
                        AIAction.OpenCalendar()
                )
        )

        // Phone/Dial
        suggestions.add(
                createSuggestion(
                        "📞 Make a Call",
                        "Open dialer to call someone",
                        Suggestion.SuggestionIcon.PHONE,
                        AIAction.DialNumber("")
                )
        )

        // SMS
        suggestions.add(
                createSuggestion(
                        "💬 Send Text Message",
                        "Compose a new SMS",
                        Suggestion.SuggestionIcon.SMS,
                        AIAction.SendSMS("", "")
                )
        )

        // Set Alarm
        suggestions.add(
                createSuggestion(
                        "⏰ Set Alarm",
                        "Create a new alarm",
                        Suggestion.SuggestionIcon.ALARM,
                        AIAction.SetAlarm(8, 0, "Alarm")
                )
        )

        // Set Timer
        suggestions.add(
                createSuggestion(
                        "⏱️ Set Timer",
                        "Start a countdown timer",
                        Suggestion.SuggestionIcon.TIMER,
                        AIAction.SetTimer(300, "Timer")
                )
        )

        // Web Search
        suggestions.add(
                createSuggestion(
                        "🔍 Web Search",
                        "Search the internet",
                        Suggestion.SuggestionIcon.SEARCH,
                        AIAction.WebSearch("")
                )
        )

        // Open URL
        suggestions.add(
                createSuggestion(
                        "🌐 Open Website",
                        "Go to a URL",
                        Suggestion.SuggestionIcon.WEB,
                        AIAction.OpenURL("https://google.com")
                )
        )

        // Maps
        suggestions.add(
                createSuggestion(
                        "🗺️ Open Maps",
                        "Find places or get directions",
                        Suggestion.SuggestionIcon.MAP,
                        AIAction.OpenMaps(query = "")
                )
        )

        // Navigate
        suggestions.add(
                createSuggestion(
                        "🧭 Navigate To...",
                        "Start turn-by-turn navigation",
                        Suggestion.SuggestionIcon.MAP,
                        AIAction.OpenMaps(query = "", navigate = true)
                )
        )

        // Email
        suggestions.add(
                createSuggestion(
                        "✉️ Send Email",
                        "Compose a new email",
                        Suggestion.SuggestionIcon.EMAIL,
                        AIAction.SendEmail("", null, null)
                )
        )

        // Camera
        suggestions.add(
                createSuggestion(
                        "📷 Take Photo",
                        "Open camera to take a picture",
                        Suggestion.SuggestionIcon.CAMERA,
                        AIAction.CaptureMedia(video = false)
                )
        )

        // Video
        suggestions.add(
                createSuggestion(
                        "🎥 Record Video",
                        "Open camera to record video",
                        Suggestion.SuggestionIcon.VIDEO,
                        AIAction.CaptureMedia(video = true)
                )
        )

        // Share
        suggestions.add(
                createSuggestion(
                        "📤 Share Text",
                        "Share content with other apps",
                        Suggestion.SuggestionIcon.SHARE,
                        AIAction.ShareText("")
                )
        )

        // Copy
        suggestions.add(
                createSuggestion(
                        "📋 Copy to Clipboard",
                        "Copy text to clipboard",
                        Suggestion.SuggestionIcon.COPY,
                        AIAction.CopyToClipboard("")
                )
        )

        // Play Media
        suggestions.add(
                createSuggestion(
                        "🎵 Play Music",
                        "Search and play music",
                        Suggestion.SuggestionIcon.MUSIC,
                        AIAction.PlayMedia("")
                )
        )

        // WiFi Settings
        suggestions.add(
                createSuggestion(
                        "📶 WiFi Settings",
                        "Open WiFi settings",
                        Suggestion.SuggestionIcon.SETTINGS,
                        AIAction.OpenSettings(AIAction.SettingsSection.WIFI)
                )
        )

        // Bluetooth Settings
        suggestions.add(
                createSuggestion(
                        "🔵 Bluetooth Settings",
                        "Open Bluetooth settings",
                        Suggestion.SuggestionIcon.SETTINGS,
                        AIAction.OpenSettings(AIAction.SettingsSection.BLUETOOTH)
                )
        )

        // Display Settings
        suggestions.add(
                createSuggestion(
                        "🔆 Display Settings",
                        "Adjust brightness and display",
                        Suggestion.SuggestionIcon.SETTINGS,
                        AIAction.OpenSettings(AIAction.SettingsSection.DISPLAY)
                )
        )

        // Sound Settings
        suggestions.add(
                createSuggestion(
                        "🔊 Sound Settings",
                        "Adjust volume and sounds",
                        Suggestion.SuggestionIcon.SETTINGS,
                        AIAction.OpenSettings(AIAction.SettingsSection.SOUND)
                )
        )

        // Battery Settings
        suggestions.add(
                createSuggestion(
                        "🔋 Battery Settings",
                        "Check battery usage",
                        Suggestion.SuggestionIcon.SETTINGS,
                        AIAction.OpenSettings(AIAction.SettingsSection.BATTERY)
                )
        )

        return suggestions.take(maxSuggestions).mapIndexed { index, suggestion ->
            suggestion.copy(priority = suggestions.size - index)
        }
    }

    private fun createSuggestion(
            title: String,
            description: String,
            icon: Suggestion.SuggestionIcon,
            action: AIAction?
    ): Suggestion {
        return Suggestion(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description,
                action = action,
                icon = icon
        )
    }

    /** Build messages array in ChatML format for the model */
    private fun buildMessages(
            userMessage: String,
            screenState: ScreenState?,
            conversationHistory: List<ChatMessage>,
            memory: List<MemoryItem>
    ): List<Map<String, String>> {
        val messages = mutableListOf<Map<String, String>>()

        // System message with capabilities
        var systemContent = SYSTEM_PROMPT

        // Add memory context
        if (memory.isNotEmpty()) {
            systemContent += "\n\n## Remembered Information:\n"
            memory.forEach { item -> systemContent += "- ${item.key}: ${item.value}\n" }
        }

        messages.add(mapOf("role" to "system", "content" to systemContent))

        // Add conversation history (limited for speed)
        conversationHistory.takeLast(4).forEach { msg ->
            messages.add(
                    mapOf(
                            "role" to
                                    when (msg.role) {
                                        ChatMessage.Role.USER -> "user"
                                        ChatMessage.Role.ASSISTANT -> "assistant"
                                        ChatMessage.Role.SYSTEM -> "system"
                                    },
                            "content" to msg.content
                    )
            )
        }

        // Current user message with screen context
        val userContent = buildString {
            if (screenState != null) {
                appendLine("[Current Screen]")
                appendLine(screenState.toPromptContext())
                appendLine()
            }
            appendLine("[User Request]")
            append(userMessage)
        }

        messages.add(mapOf("role" to "user", "content" to userContent))

        return messages
    }

    /** Smart response generation for testing/demo Will be replaced by actual Cactus inference */
    private fun generateSmartResponse(
            userMessage: String,
            screenState: ScreenState?,
            memory: List<MemoryItem>
    ): String {
        val lowerMessage = userMessage.lowercase()
        val screenContext = screenState?.toPromptContext() ?: ""

        return when {
            // Screen analysis requests
            lowerMessage.contains("what") &&
                    (lowerMessage.contains("screen") || lowerMessage.contains("see")) -> {
                val elements =
                        screenState?.flattenElements()?.take(10)?.mapNotNull { it.getLabel() }
                                ?: emptyList()
                val description =
                        if (elements.isNotEmpty()) {
                            "I can see: ${elements.joinToString(", ")}"
                        } else {
                            "I can see the current screen but couldn't identify specific elements."
                        }
                """{"thought":"User wants to know what's on screen","response":"$description","actions":[],"complete":true}"""
            }

            // Click/tap actions
            lowerMessage.contains("click") ||
                    lowerMessage.contains("tap") ||
                    lowerMessage.contains("press") -> {
                val target = extractTarget(lowerMessage)
                """{"thought":"User wants to click on '$target'","response":"Tapping on $target","actions":[{"type":"click","target":"$target"}],"complete":true}"""
            }

            // Type/enter actions
            lowerMessage.contains("type") ||
                    lowerMessage.contains("enter") ||
                    lowerMessage.contains("write") -> {
                val text =
                        extractQuotedText(userMessage)
                                ?: extractAfterKeyword(
                                        userMessage,
                                        listOf("type", "enter", "write")
                                )
                """{"thought":"User wants to type text","response":"Typing '$text'","actions":[{"type":"type","target":"input","text":"$text"}],"complete":true}"""
            }

            // Scroll actions
            lowerMessage.contains("scroll") -> {
                val direction =
                        when {
                            lowerMessage.contains("up") -> "up"
                            lowerMessage.contains("down") -> "down"
                            lowerMessage.contains("left") -> "left"
                            lowerMessage.contains("right") -> "right"
                            else -> "down"
                        }
                """{"thought":"User wants to scroll $direction","response":"Scrolling $direction","actions":[{"type":"scroll","direction":"$direction"}],"complete":true}"""
            }

            // Navigation
            lowerMessage.contains("go back") || lowerMessage == "back" -> {
                """{"thought":"User wants to go back","response":"Going back","actions":[{"type":"back"}],"complete":true}"""
            }
            lowerMessage.contains("go home") || lowerMessage.contains("home screen") -> {
                """{"thought":"User wants to go home","response":"Going to home screen","actions":[{"type":"home"}],"complete":true}"""
            }

            // Open app
            lowerMessage.contains("open") -> {
                val appName = extractAfterKeyword(lowerMessage, listOf("open", "launch", "start"))
                """{"thought":"User wants to open $appName","response":"Opening $appName","actions":[{"type":"open_app","app":"$appName"}],"complete":true}"""
            }

            // Help
            lowerMessage.contains("help") || lowerMessage.contains("what can you") -> {
                """{"thought":"User needs help","response":"I'm Omni, your on-device AI assistant! I can:\n\n• **See** what's on your screen\n• **Click** buttons and links\n• **Type** text in fields\n• **Scroll** through content\n• **Open apps** - try: Settings, Chrome, Messages, Phone, Camera, Clock\n• **Navigate** - go back, go home\n• **Remember** things you tell me\n\n📱 **Device Actions:**\n• \"open calendar\" / \"create event\"\n• \"call 555-1234\" / \"send text to 555-1234\"\n• \"set alarm for 7am\" / \"set timer 5 minutes\"\n• \"search for weather\" / \"open google.com\"\n• \"navigate to coffee shop\" / \"show maps\"\n• \"send email to user@mail.com\"\n• \"share this text\" / \"copy hello\"\n• \"take a photo\" / \"record video\"\n• \"open wifi settings\" / \"open bluetooth settings\"\n\nTry saying:\n• \"open settings\"\n• \"what's on my screen?\"\n• \"set alarm for 8am\"","actions":[],"complete":true}"""
            }

            // Calculator - might not be on emulator
            lowerMessage.contains("calculate") || lowerMessage.contains("calculator") -> {
                """{"thought":"User wants to use calculator - may not be on emulator","response":"I'll try to open Calculator. Note: Some emulators don't have Calculator pre-installed. If it doesn't work, try 'open settings' or 'open chrome' instead.","actions":[{"type":"open_app","app":"Calculator"}],"complete":false}"""
            }

            // Settings - always available
            lowerMessage.contains("settings") -> {
                """{"thought":"Opening Settings app","response":"Opening Settings","actions":[{"type":"open_app","app":"Settings"}],"complete":true}"""
            }

            // Chrome/Browser
            lowerMessage.contains("chrome") ||
                    lowerMessage.contains("browser") ||
                    lowerMessage.contains("web") -> {
                """{"thought":"Opening browser","response":"Opening Chrome browser","actions":[{"type":"open_app","app":"Chrome"}],"complete":true}"""
            }

            // Messages
            lowerMessage.contains("message") ||
                    lowerMessage.contains("sms") ||
                    lowerMessage.contains("text") -> {
                """{"thought":"Opening Messages app","response":"Opening Messages","actions":[{"type":"open_app","app":"Messages"}],"complete":true}"""
            }

            // Phone/Dialer
            lowerMessage.contains("phone") ||
                    lowerMessage.contains("call") ||
                    lowerMessage.contains("dial") -> {
                """{"thought":"Opening Phone app","response":"Opening Phone","actions":[{"type":"open_app","app":"Phone"}],"complete":true}"""
            }

            // Contacts
            lowerMessage.contains("contact") -> {
                """{"thought":"Opening Contacts","response":"Opening Contacts","actions":[{"type":"open_app","app":"Contacts"}],"complete":true}"""
            }

            // Camera
            lowerMessage.contains("camera") || lowerMessage.contains("photo") -> {
                """{"thought":"Opening Camera","response":"Opening Camera","actions":[{"type":"open_app","app":"Camera"}],"complete":true}"""
            }

            // Clock/Alarm
            lowerMessage.contains("clock") ||
                    lowerMessage.contains("alarm") ||
                    lowerMessage.contains("timer") -> {
                """{"thought":"Opening Clock app","response":"Opening Clock","actions":[{"type":"open_app","app":"Clock"}],"complete":true}"""
            }

            // Files
            lowerMessage.contains("file") || lowerMessage.contains("download") -> {
                """{"thought":"Opening Files app","response":"Opening Files","actions":[{"type":"open_app","app":"Files"}],"complete":true}"""
            }

            // Play Store
            lowerMessage.contains("play store") ||
                    lowerMessage.contains("app store") ||
                    lowerMessage.contains("install") -> {
                """{"thought":"Opening Play Store","response":"Opening Google Play Store","actions":[{"type":"open_app","app":"Play Store"}],"complete":true}"""
            }

            // ═══════════════════════════════════════════════════════════════════════════
            // DEVICE INTENT ACTIONS - Smart response handlers
            // ═══════════════════════════════════════════════════════════════════════════

            // Calendar actions
            lowerMessage.contains("calendar") && !lowerMessage.contains("event") -> {
                """{"thought":"Opening calendar","response":"Opening your calendar","actions":[{"type":"open_calendar"}],"complete":true}"""
            }
            lowerMessage.contains("create event") ||
                    lowerMessage.contains("add event") ||
                    lowerMessage.contains("schedule") ||
                    lowerMessage.contains("meeting") -> {
                val title =
                        extractQuotedText(userMessage)
                                ?: extractAfterKeyword(
                                        lowerMessage,
                                        listOf("event", "meeting", "schedule")
                                )
                """{"thought":"Creating calendar event","response":"Creating a calendar event: $title","actions":[{"type":"open_calendar","title":"$title"}],"complete":true}"""
            }

            // Dial/Call actions
            (lowerMessage.contains("dial") ||
                    (lowerMessage.contains("call") && !lowerMessage.contains("recall"))) -> {
                val phoneNumber =
                        extractPhoneNumber(userMessage)
                                ?: extractAfterKeyword(lowerMessage, listOf("dial", "call"))
                if (lowerMessage.contains("call")) {
                    """{"thought":"Calling $phoneNumber","response":"Calling $phoneNumber","actions":[{"type":"dial","phone":"$phoneNumber"}],"complete":true}"""
                } else {
                    """{"thought":"Opening dialer with $phoneNumber","response":"Opening dialer with $phoneNumber","actions":[{"type":"dial","phone":"$phoneNumber"}],"complete":true}"""
                }
            }

            // SMS/Text message actions
            (lowerMessage.contains("text ") ||
                    lowerMessage.contains("sms") ||
                    lowerMessage.contains("send message to")) -> {
                val phoneNumber = extractPhoneNumber(userMessage) ?: ""
                val message = extractQuotedText(userMessage) ?: ""
                """{"thought":"Composing SMS","response":"Composing SMS to $phoneNumber","actions":[{"type":"sms","phone":"$phoneNumber","message":"$message"}],"complete":true}"""
            }

            // Alarm actions
            lowerMessage.contains("set alarm") ||
                    (lowerMessage.contains("alarm") && lowerMessage.contains("for")) ||
                    lowerMessage.contains("wake me") -> {
                val timeInfo = extractTime(userMessage)
                """{"thought":"Setting alarm","response":"Setting alarm for ${timeInfo.first}:${String.format("%02d", timeInfo.second)}","actions":[{"type":"set_alarm","hour":${timeInfo.first},"minute":${timeInfo.second}}],"complete":true}"""
            }

            // Timer actions
            lowerMessage.contains("set timer") ||
                    lowerMessage.contains("timer for") ||
                    lowerMessage.contains("countdown") -> {
                val seconds = extractTimerSeconds(userMessage)
                val minutes = seconds / 60
                val secs = seconds % 60
                """{"thought":"Setting timer","response":"Setting timer for ${if (minutes > 0) "${minutes}m " else ""}${secs}s","actions":[{"type":"set_timer","seconds":$seconds}],"complete":true}"""
            }

            // Web search
            lowerMessage.contains("search for") ||
                    lowerMessage.contains("search ") ||
                    lowerMessage.contains("google") ||
                    lowerMessage.contains("look up") -> {
                val query =
                        extractAfterKeyword(
                                lowerMessage,
                                listOf("search for", "search", "google", "look up")
                        )
                """{"thought":"Searching the web","response":"Searching for: $query","actions":[{"type":"web_search","query":"$query"}],"complete":true}"""
            }

            // Open URL
            lowerMessage.contains("go to ") &&
                    (lowerMessage.contains(".com") ||
                            lowerMessage.contains(".org") ||
                            lowerMessage.contains(".net") ||
                            lowerMessage.contains("http")) -> {
                val url = extractUrl(userMessage)
                """{"thought":"Opening URL","response":"Opening $url","actions":[{"type":"open_url","url":"$url"}],"complete":true}"""
            }

            // Maps and Navigation
            lowerMessage.contains("navigate to") ||
                    lowerMessage.contains("directions to") ||
                    lowerMessage.contains("take me to") -> {
                val destination =
                        extractAfterKeyword(
                                lowerMessage,
                                listOf("navigate to", "directions to", "take me to")
                        )
                """{"thought":"Starting navigation","response":"Starting navigation to $destination","actions":[{"type":"navigate","query":"$destination"}],"complete":true}"""
            }
            lowerMessage.contains("show map") ||
                    lowerMessage.contains("find nearby") ||
                    lowerMessage.contains("where is") ||
                    (lowerMessage.contains("map") && lowerMessage.contains("of")) -> {
                val query =
                        extractAfterKeyword(
                                lowerMessage,
                                listOf("find nearby", "where is", "map of", "show map of", "maps")
                        )
                """{"thought":"Opening maps","response":"Opening maps: $query","actions":[{"type":"maps","query":"$query"}],"complete":true}"""
            }

            // Email
            lowerMessage.contains("send email") ||
                    lowerMessage.contains("email to") ||
                    lowerMessage.contains("compose email") -> {
                val emailAddress = extractEmail(userMessage) ?: ""
                val subject = extractQuotedText(userMessage) ?: ""
                """{"thought":"Composing email","response":"Composing email to $emailAddress","actions":[{"type":"email","to":"$emailAddress","subject":"$subject"}],"complete":true}"""
            }

            // Copy to clipboard
            lowerMessage.contains("copy ") && !lowerMessage.contains("copy text to") -> {
                val textToCopy =
                        extractQuotedText(userMessage)
                                ?: extractAfterKeyword(lowerMessage, listOf("copy"))
                """{"thought":"Copying to clipboard","response":"Copied to clipboard","actions":[{"type":"copy","text":"$textToCopy"}],"complete":true}"""
            }

            // Share
            lowerMessage.contains("share ") || lowerMessage.contains("share this") -> {
                val textToShare =
                        extractQuotedText(userMessage)
                                ?: extractAfterKeyword(lowerMessage, listOf("share"))
                """{"thought":"Opening share dialog","response":"Opening share dialog","actions":[{"type":"share","text":"$textToShare"}],"complete":true}"""
            }

            // Camera
            lowerMessage.contains("take a photo") ||
                    lowerMessage.contains("take photo") ||
                    lowerMessage.contains("take picture") ||
                    (lowerMessage.contains("camera") && !lowerMessage.contains("open")) -> {
                """{"thought":"Opening camera","response":"Opening camera to take a photo","actions":[{"type":"camera"}],"complete":true}"""
            }

            // Video
            lowerMessage.contains("record video") ||
                    lowerMessage.contains("take video") ||
                    lowerMessage.contains("video camera") -> {
                """{"thought":"Opening video camera","response":"Opening camera to record video","actions":[{"type":"video"}],"complete":true}"""
            }

            // Play music/media
            lowerMessage.contains("play ") &&
                    (lowerMessage.contains("music") ||
                            lowerMessage.contains("song") ||
                            lowerMessage.contains("podcast") ||
                            lowerMessage.contains("by ")) -> {
                val query = extractAfterKeyword(lowerMessage, listOf("play"))
                """{"thought":"Playing media","response":"Playing: $query","actions":[{"type":"play_media","query":"$query"}],"complete":true}"""
            }

            // Settings sections
            lowerMessage.contains("wifi settings") ||
                    (lowerMessage.contains("wifi") && lowerMessage.contains("setting")) -> {
                """{"thought":"Opening WiFi settings","response":"Opening WiFi settings","actions":[{"type":"settings","section":"wifi"}],"complete":true}"""
            }
            lowerMessage.contains("bluetooth settings") ||
                    (lowerMessage.contains("bluetooth") && lowerMessage.contains("setting")) -> {
                """{"thought":"Opening Bluetooth settings","response":"Opening Bluetooth settings","actions":[{"type":"settings","section":"bluetooth"}],"complete":true}"""
            }
            lowerMessage.contains("display settings") ||
                    lowerMessage.contains("brightness settings") -> {
                """{"thought":"Opening display settings","response":"Opening display settings","actions":[{"type":"settings","section":"display"}],"complete":true}"""
            }
            lowerMessage.contains("sound settings") || lowerMessage.contains("volume settings") -> {
                """{"thought":"Opening sound settings","response":"Opening sound settings","actions":[{"type":"settings","section":"sound"}],"complete":true}"""
            }
            lowerMessage.contains("battery settings") ||
                    lowerMessage.contains("power settings") -> {
                """{"thought":"Opening battery settings","response":"Opening battery settings","actions":[{"type":"settings","section":"battery"}],"complete":true}"""
            }
            lowerMessage.contains("notification settings") -> {
                """{"thought":"Opening notification settings","response":"Opening notification settings","actions":[{"type":"settings","section":"notifications"}],"complete":true}"""
            }
            lowerMessage.contains("location settings") || lowerMessage.contains("gps settings") -> {
                """{"thought":"Opening location settings","response":"Opening location settings","actions":[{"type":"settings","section":"location"}],"complete":true}"""
            }
            lowerMessage.contains("accessibility settings") -> {
                """{"thought":"Opening accessibility settings","response":"Opening accessibility settings","actions":[{"type":"settings","section":"accessibility"}],"complete":true}"""
            }

            // Default
            else -> {
                """{"thought":"Processing request","response":"I understand. Let me help you with that. Could you be more specific about what you'd like me to do on the screen?","actions":[],"complete":true}"""
            }
        }
    }

    private fun extractTarget(message: String): String {
        val keywords = listOf("click", "tap", "press", "on", "the", "button", "link")
        var result = message
        keywords.forEach { result = result.replace(it, "", ignoreCase = true) }
        return result.trim().ifEmpty { "element" }
    }

    private fun extractQuotedText(message: String): String? {
        val patterns = listOf(""""([^"]+)"""", """'([^']+)'""", """`([^`]+)`""")
        for (pattern in patterns) {
            val match = pattern.toRegex().find(message)
            if (match != null) {
                return match.groupValues.getOrNull(1)
            }
        }
        return null
    }

    private fun extractAfterKeyword(message: String, keywords: List<String>): String {
        for (keyword in keywords) {
            val index = message.lowercase().indexOf(keyword)
            if (index != -1) {
                return message.substring(index + keyword.length).trim()
            }
        }
        return message
    }

    /** Extract phone number from message */
    private fun extractPhoneNumber(message: String): String? {
        // Match various phone number formats
        val patterns =
                listOf(
                        """\+?[\d\s\-\(\)]{7,15}""", // International format
                        """\d{3}[\s\-]?\d{3}[\s\-]?\d{4}""", // US format
                        """\(\d{3}\)\s?\d{3}[\s\-]?\d{4}""" // (xxx) xxx-xxxx
                )
        for (pattern in patterns) {
            val match = pattern.toRegex().find(message)
            if (match != null) {
                return match.value.replace(Regex("[\\s\\-\\(\\)]"), "")
            }
        }
        return null
    }

    /** Extract email address from message */
    private fun extractEmail(message: String): String? {
        val emailPattern = """[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"""
        val match = emailPattern.toRegex().find(message)
        return match?.value
    }

    /** Extract URL from message */
    private fun extractUrl(message: String): String {
        // Match URLs with or without protocol
        val urlPattern =
                """(https?://)?[\w\-\.]+\.(com|org|net|io|co|edu|gov|app|dev)[\w\-\._~:/?#\[\]@!${'$'}&'()*+,;=]*"""
        val match = urlPattern.toRegex(RegexOption.IGNORE_CASE).find(message)
        return match?.value ?: message
    }

    /** Extract time from message (returns hour, minute pair) */
    private fun extractTime(message: String): Pair<Int, Int> {
        val lowerMessage = message.lowercase()

        // Match "7:30 am", "7:30am", "7 am", "7am", "19:30", etc.
        val timePatterns =
                listOf(
                        """(\d{1,2}):(\d{2})\s*(am|pm)?""", // 7:30 am or 19:30
                        """(\d{1,2})\s*(am|pm)""", // 7 am or 7am
                        """(\d{1,2})\s*o'?clock""" // 7 o'clock
                )

        for (pattern in timePatterns) {
            val match = pattern.toRegex(RegexOption.IGNORE_CASE).find(lowerMessage)
            if (match != null) {
                var hour = match.groupValues[1].toIntOrNull() ?: 8
                val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                val amPm = match.groupValues.lastOrNull { it == "am" || it == "pm" }

                // Adjust for PM
                if (amPm == "pm" && hour < 12) hour += 12
                if (amPm == "am" && hour == 12) hour = 0

                return Pair(hour, minute)
            }
        }

        // Default to 8:00 AM
        return Pair(8, 0)
    }

    /** Extract timer duration in seconds from message */
    private fun extractTimerSeconds(message: String): Int {
        val lowerMessage = message.lowercase()
        var totalSeconds = 0

        // Match hours
        val hourMatch = """(\d+)\s*h(our)?s?""".toRegex().find(lowerMessage)
        if (hourMatch != null) {
            totalSeconds += (hourMatch.groupValues[1].toIntOrNull() ?: 0) * 3600
        }

        // Match minutes
        val minMatch = """(\d+)\s*m(in(ute)?s?)?""".toRegex().find(lowerMessage)
        if (minMatch != null) {
            totalSeconds += (minMatch.groupValues[1].toIntOrNull() ?: 0) * 60
        }

        // Match seconds
        val secMatch = """(\d+)\s*s(ec(ond)?s?)?""".toRegex().find(lowerMessage)
        if (secMatch != null) {
            totalSeconds += secMatch.groupValues[1].toIntOrNull() ?: 0
        }

        // If just a number, assume minutes
        if (totalSeconds == 0) {
            val numMatch = """(\d+)""".toRegex().find(lowerMessage)
            totalSeconds = (numMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1) * 60
        }

        return if (totalSeconds > 0) totalSeconds else 60
    }

    private fun extractMemoryUpdates(response: String): List<MemoryItem> {
        return try {
            val json = JsonParser.parseString(response).asJsonObject
            val memoryArray = json.getAsJsonArray("memory") ?: return emptyList()

            memoryArray.mapNotNull { element ->
                val obj = element.asJsonObject
                MemoryItem(
                        key = obj.get("key")?.asString ?: return@mapNotNull null,
                        value = obj.get("value")?.asString ?: return@mapNotNull null,
                        category = obj.get("category")?.asString ?: "general",
                        timestamp = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun parseActions(response: String): ActionPlan {
        // First, try to extract clean text if response looks like JSON
        val cleanedResponse = cleanResponseText(response)

        return try {
            // Try to find JSON in the response (model might include extra text)
            val jsonString = extractJsonFromResponse(response)
            if (jsonString != null) {
                val json = JsonParser.parseString(jsonString).asJsonObject

                val thought = json.get("thought")?.asString ?: ""
                val responseText = json.get("response")?.asString ?: cleanedResponse
                val isComplete = json.get("complete")?.asBoolean ?: true
                val actionsArray = json.getAsJsonArray("actions")

                if (actionsArray == null || actionsArray.size() == 0) {
                    return ActionPlan(
                            reasoning = thought,
                            actions = listOf(AIAction.Respond(responseText)),
                            isComplete = isComplete
                    )
                }

                val actions = mutableListOf<AIAction>()

                // Add response first
                actions.add(AIAction.Respond(responseText))

                for (actionJson in actionsArray) {
                    val actionObj = actionJson.asJsonObject
                    val type = actionObj.get("type")?.asString ?: continue

                    val action: AIAction? =
                            when (type) {
                                "click" ->
                                        AIAction.Click(
                                                target = actionObj.get("target")?.asString ?: "",
                                                index = actionObj.get("index")?.asInt
                                        )
                                "type" ->
                                        AIAction.Type(
                                                target = actionObj.get("target")?.asString ?: "",
                                                text = actionObj.get("text")?.asString ?: "",
                                                clearFirst = actionObj.get("clear")?.asBoolean
                                                                ?: true
                                        )
                                "scroll" ->
                                        AIAction.Scroll(
                                                direction =
                                                        when (actionObj
                                                                        .get("direction")
                                                                        ?.asString
                                                                        ?.lowercase()
                                                        ) {
                                                            "up" -> AIAction.ScrollDirection.UP
                                                            "down" -> AIAction.ScrollDirection.DOWN
                                                            "left" -> AIAction.ScrollDirection.LEFT
                                                            "right" ->
                                                                    AIAction.ScrollDirection.RIGHT
                                                            else -> AIAction.ScrollDirection.DOWN
                                                        }
                                        )
                                "back" -> AIAction.Back
                                "home" -> AIAction.Home
                                "open_app" -> AIAction.OpenApp(actionObj.get("app")?.asString ?: "")
                                "wait" -> AIAction.Wait(actionObj.get("ms")?.asLong ?: 1000)
                                // Device Intent Actions
                                "open_calendar" ->
                                        AIAction.OpenCalendar(
                                                title = actionObj.get("title")?.asString,
                                                description =
                                                        actionObj.get("description")?.asString,
                                                location = actionObj.get("location")?.asString,
                                                startTime = actionObj.get("start_time")?.asLong,
                                                endTime = actionObj.get("end_time")?.asLong
                                        )
                                "dial" ->
                                        AIAction.DialNumber(
                                                phoneNumber = actionObj.get("phone")?.asString ?: ""
                                        )
                                "call" ->
                                        AIAction.CallNumber(
                                                phoneNumber = actionObj.get("phone")?.asString ?: ""
                                        )
                                "sms" ->
                                        AIAction.SendSMS(
                                                phoneNumber = actionObj.get("phone")?.asString
                                                                ?: "",
                                                message = actionObj.get("message")?.asString ?: ""
                                        )
                                "open_url" ->
                                        AIAction.OpenURL(url = actionObj.get("url")?.asString ?: "")
                                "web_search" ->
                                        AIAction.WebSearch(
                                                query = actionObj.get("query")?.asString ?: ""
                                        )
                                "set_alarm" ->
                                        AIAction.SetAlarm(
                                                hour = actionObj.get("hour")?.asInt ?: 8,
                                                minute = actionObj.get("minute")?.asInt ?: 0,
                                                message = actionObj.get("message")?.asString
                                        )
                                "set_timer" ->
                                        AIAction.SetTimer(
                                                seconds = actionObj.get("seconds")?.asInt ?: 60,
                                                message = actionObj.get("message")?.asString
                                        )
                                "share" ->
                                        AIAction.ShareText(
                                                text = actionObj.get("text")?.asString ?: "",
                                                subject = actionObj.get("subject")?.asString
                                        )
                                "copy" ->
                                        AIAction.CopyToClipboard(
                                                text = actionObj.get("text")?.asString ?: ""
                                        )
                                "email" ->
                                        AIAction.SendEmail(
                                                to = actionObj.get("to")?.asString ?: "",
                                                subject = actionObj.get("subject")?.asString,
                                                body = actionObj.get("body")?.asString
                                        )
                                "maps" ->
                                        AIAction.OpenMaps(
                                                query = actionObj.get("query")?.asString,
                                                latitude = actionObj.get("lat")?.asDouble,
                                                longitude = actionObj.get("lng")?.asDouble,
                                                navigate = false
                                        )
                                "navigate" ->
                                        AIAction.OpenMaps(
                                                query = actionObj.get("query")?.asString,
                                                latitude = actionObj.get("lat")?.asDouble,
                                                longitude = actionObj.get("lng")?.asDouble,
                                                navigate = true
                                        )
                                "play_media" ->
                                        AIAction.PlayMedia(
                                                query = actionObj.get("query")?.asString ?: "",
                                                artist = actionObj.get("artist")?.asString,
                                                album = actionObj.get("album")?.asString
                                        )
                                "camera" -> AIAction.CaptureMedia(video = false)
                                "video" -> AIAction.CaptureMedia(video = true)
                                "settings" ->
                                        AIAction.OpenSettings(
                                                section =
                                                        when (actionObj
                                                                        .get("section")
                                                                        ?.asString
                                                                        ?.lowercase()
                                                        ) {
                                                            "wifi" -> AIAction.SettingsSection.WIFI
                                                            "bluetooth" ->
                                                                    AIAction.SettingsSection
                                                                            .BLUETOOTH
                                                            "display" ->
                                                                    AIAction.SettingsSection.DISPLAY
                                                            "sound" ->
                                                                    AIAction.SettingsSection.SOUND
                                                            "battery" ->
                                                                    AIAction.SettingsSection.BATTERY
                                                            "apps" -> AIAction.SettingsSection.APPS
                                                            "notifications" ->
                                                                    AIAction.SettingsSection
                                                                            .NOTIFICATIONS
                                                            "location" ->
                                                                    AIAction.SettingsSection
                                                                            .LOCATION
                                                            "security" ->
                                                                    AIAction.SettingsSection
                                                                            .SECURITY
                                                            "accessibility" ->
                                                                    AIAction.SettingsSection
                                                                            .ACCESSIBILITY
                                                            else -> AIAction.SettingsSection.MAIN
                                                        }
                                        )
                                else -> null
                            }
                    action?.let { actions.add(it) }
                }

                ActionPlan(reasoning = thought, actions = actions, isComplete = isComplete)
            } else {
                // No JSON found - treat as plain text response
                ActionPlan(
                        reasoning = "",
                        actions = listOf(AIAction.Respond(cleanedResponse)),
                        isComplete = true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse actions from response", e)
            // Return cleaned response, not raw JSON
            ActionPlan(
                    reasoning = "Parse error",
                    actions = listOf(AIAction.Respond(cleanedResponse)),
                    isComplete = true
            )
        }
    }

    /**
     * Try to extract JSON object from response text. The model might include extra text before or
     * after the JSON.
     */
    private fun extractJsonFromResponse(response: String): String? {
        val trimmed = response.trim()

        // If it starts with {, try to find matching }
        if (trimmed.startsWith("{")) {
            var braceCount = 0
            var endIndex = -1
            for (i in trimmed.indices) {
                when (trimmed[i]) {
                    '{' -> braceCount++
                    '}' -> {
                        braceCount--
                        if (braceCount == 0) {
                            endIndex = i
                            break
                        }
                    }
                }
            }
            if (endIndex > 0) {
                return trimmed.substring(0, endIndex + 1)
            }
        }

        // Try to find JSON anywhere in the response
        val jsonStart = trimmed.indexOf('{')
        if (jsonStart >= 0) {
            var braceCount = 0
            var endIndex = -1
            for (i in jsonStart until trimmed.length) {
                when (trimmed[i]) {
                    '{' -> braceCount++
                    '}' -> {
                        braceCount--
                        if (braceCount == 0) {
                            endIndex = i
                            break
                        }
                    }
                }
            }
            if (endIndex > jsonStart) {
                return trimmed.substring(jsonStart, endIndex + 1)
            }
        }

        return null
    }

    /**
     * Clean up response text for display. Removes JSON formatting if present and extracts readable
     * content.
     */
    private fun cleanResponseText(response: String): String {
        val trimmed = response.trim()

        // If it looks like JSON, try to extract the "response" field
        if (trimmed.startsWith("{") && trimmed.contains("\"response\"")) {
            try {
                val json =
                        JsonParser.parseString(extractJsonFromResponse(trimmed) ?: trimmed)
                                .asJsonObject
                return json.get("response")?.asString ?: trimmed
            } catch (e: Exception) {
                // Fall through to regex extraction
            }

            // Try regex extraction as fallback
            val responseMatch = """"response"\s*:\s*"([^"]+)"""".toRegex().find(trimmed)
            if (responseMatch != null) {
                return responseMatch.groupValues[1]
            }
        }

        // Return as-is if it's not JSON
        return trimmed
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STREAMING SUGGESTION GENERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generate suggestions with streaming output so users can see AI thinking in real-time. Uses
     * the onToken callback from Cactus SDK to emit tokens as they're generated.
     *
     * @param focusRegion Optional focus region to limit AI attention to a specific screen area
     */
    override fun generateSuggestionsStreaming(
            screenState: ScreenState,
            maxSuggestions: Int,
            focusRegion: FocusRegion?
    ): kotlinx.coroutines.flow.Flow<SuggestionStreamEvent> = callbackFlow {
        try {
            if (!isModelLoaded) {
                trySend(SuggestionStreamEvent.Error("Model not loaded. Call loadModel() first."))
                close()
                return@callbackFlow
            }

            // Use focused context if a focus region is set
            val screenContext =
                    if (focusRegion != null) {
                        screenState.toPromptContextWithFocus(focusRegion)
                    } else {
                        screenState.toPromptContext()
                    }

            val focusInfo = if (focusRegion != null) " [FOCUS AREA ACTIVE]" else ""
            Log.d(
                    TAG,
                    "Starting streaming suggestion generation for: ${screenState.packageName}$focusInfo"
            )

            DebugLogManager.info(
                    TAG,
                    "Starting streaming suggestion generation",
                    "App: ${screenState.packageName}\n" +
                            "Max suggestions: $maxSuggestions\n" +
                            "Focus region: ${focusRegion?.bounds ?: "Full screen"}"
            )

            if (!cactusAvailable || cactusLM == null) {
                // Fallback to non-streaming for smart suggestions
                DebugLogManager.info(TAG, "AI not available, using fallback")
                val suggestions =
                        generateSmartSuggestions(screenState, screenContext, maxSuggestions)
                trySend(SuggestionStreamEvent.Complete(suggestions))
                close()
                return@callbackFlow
            }

            // Build the prompt with focus region awareness
            val suggestionPrompt = buildStreamingPrompt(screenContext, maxSuggestions, focusRegion)

            DebugLogManager.prompt(
                    TAG,
                    "Streaming AI prompt for suggestions",
                    "Prompt length: ${suggestionPrompt.length} chars"
            )

            val cactusMessages =
                    listOf(CactusChatMessage(content = suggestionPrompt, role = "user"))
            val fullResponse = StringBuilder()

            // Generate completion with streaming via onToken callback
            // Use mutex to prevent concurrent native library access (causes SIGSEGV)
            val result = inferenceMutex.withLock {
                cactusLM!!.generateCompletion(
                        messages = cactusMessages,
                        params = CactusCompletionParams(maxTokens = 400, temperature = 0.1),
                        onToken = { token, _ ->
                            fullResponse.append(token)
                            // Emit each token so UI can show progress
                            trySend(SuggestionStreamEvent.Token(token, fullResponse.toString()))
                        }
                )
            }

            val responseContent = result?.response ?: fullResponse.toString()
            DebugLogManager.response(TAG, "Streaming complete", "Response:\n$responseContent")

            // Parse the final response into suggestions
            val suggestions =
                    if (responseContent.isNotBlank()) {
                        val cleanedResponse = cleanAIResponse(responseContent)
                        val parsed = parseSuggestionsFromAI(cleanedResponse, screenState)
                        if (parsed.isNotEmpty()) {
                            parsed
                        } else {
                            generateContextAwareFallback(screenState, screenContext, maxSuggestions)
                        }
                    } else {
                        generateSmartSuggestions(screenState, screenContext, maxSuggestions)
                    }

            trySend(SuggestionStreamEvent.Complete(suggestions))
            close()
        } catch (e: Exception) {
            Log.e(TAG, "Streaming suggestion generation failed", e)
            DebugLogManager.error(TAG, "Streaming failed", e.message ?: "Unknown error")
            trySend(SuggestionStreamEvent.Error(e.message ?: "Streaming failed"))
            close()
        }

        awaitClose {}
    }

    /** Build the prompt for streaming suggestions */
    private fun buildStreamingPrompt(
            screenContext: String,
            maxSuggestions: Int,
            focusRegion: FocusRegion? = null
    ): String {
        val focusInstruction =
                if (focusRegion != null) {
                    """
IMPORTANT: The user has selected a FOCUS AREA on the screen.
Pay special attention to elements marked as "FOCUSED ELEMENTS" - these are the user's area of interest.
Prioritize suggestions for elements within the focus area over other screen elements.
"""
                } else ""

        return """You are a mobile assistant. Based on the screen below, suggest $maxSuggestions helpful actions.
$focusInstruction
SCREEN:
$screenContext

Return ONLY a JSON array. Each object must have these exact fields:
- "title": short action name with emoji (e.g. "📞 Call number")
- "description": one sentence explanation
- "type": one of: dial, sms, search, maps, calendar, alarm, timer, email, camera, copy, share, settings_wifi, settings_bluetooth
- "value": the target (phone number, search query, address, etc.)

Example response format:
[{"title":"📞 Call 07483225245","description":"Dial this phone number","type":"dial","value":"07483225245"}]

JSON array:"""
    }
}
