package com.example.omni_link.ai

import android.content.Context
import android.util.Log
import com.cactus.CactusCompletionParams
import com.cactus.CactusInitParams
import com.cactus.CactusLM
import com.cactus.ChatMessage as CactusChatMessage
import com.example.omni_link.data.AIAction
import com.example.omni_link.data.ActionPlan
import com.example.omni_link.data.ScreenState
import com.example.omni_link.data.Suggestion
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
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
        // Performance tuning: Lower values = faster responses
        private const val DEFAULT_CONTEXT_SIZE = 1536 // Reduced from 2048
        private const val DEFAULT_MAX_TOKENS = 384 // Reduced from 512

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
    {"type": "wait", "ms": 1000}
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
""".trimIndent()
    }

    // Model state
    private var isModelLoaded = false
    private var currentModelPath: String? = null

    // Cactus LLM instance
    private var cactusLM: CactusLM? = null

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
                        Log.d(TAG, "Attempting to initialize model by slug: $actualSlug")
                        cactusLM?.initializeModel(
                                CactusInitParams(
                                        model = actualSlug,
                                        contextSize = DEFAULT_CONTEXT_SIZE
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
                        cactusLM?.initializeModel(
                                CactusInitParams(
                                        model = modelSlug,
                                        contextSize = DEFAULT_CONTEXT_SIZE
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

                                    // Add conversation history (limited for speed)
                                    conversationHistory.takeLast(4).forEach { msg ->
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
                                    val result =
                                            cactusLM!!.generateCompletion(
                                                    messages = cactusMessages,
                                                    params =
                                                            CactusCompletionParams(
                                                                    maxTokens = DEFAULT_MAX_TOKENS,
                                                                    temperature =
                                                                            0.5 // Lower = faster,
                                                                    // more
                                                                    // deterministic
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
                        return@withContext Result.failure(
                                IllegalStateException("Model not loaded. Call loadModel() first.")
                        )
                    }

                    val screenContext = screenState.toPromptContext()
                    Log.d(TAG, "Generating suggestions for screen: ${screenState.packageName}")

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
                                generateSmartSuggestions(screenState, screenContext, maxSuggestions)
                            }

                    Result.success(suggestions)
                } catch (e: Exception) {
                    Log.e(TAG, "Suggestion generation failed", e)
                    Result.failure(e)
                }
            }

    /** Generate suggestions using AI inference */
    private suspend fun generateAISuggestions(
            screenState: ScreenState,
            screenContext: String,
            maxSuggestions: Int
    ): List<Suggestion> {
        val suggestionPrompt =
                """
You are analyzing a screen and need to suggest helpful actions. Based on the current screen context, provide $maxSuggestions useful suggestions.

$screenContext

Respond with a JSON array of suggestions:
```json
[
  {
    "title": "Short action title",
    "description": "Brief description of what this does",
    "type": "click|type|scroll|navigate|app|search|share|copy|info",
    "target": "element to interact with (if applicable)"
  }
]
```

Focus on:
1. Common actions for this type of app/screen
2. Interactive elements visible on screen
3. Helpful shortcuts or tips
4. Navigation suggestions
""".trimIndent()

        val cactusMessages = listOf(CactusChatMessage(content = suggestionPrompt, role = "user"))

        val result =
                cactusLM!!.generateCompletion(
                        messages = cactusMessages,
                        params = CactusCompletionParams(maxTokens = 300, temperature = 0.7)
                )

        val responseContent = result?.response
        if (!responseContent.isNullOrBlank()) {
            return parseSuggestionsFromAI(responseContent, screenState)
        }

        return generateSmartSuggestions(screenState, screenContext, maxSuggestions)
    }

    /** Parse AI-generated suggestions from response */
    private fun parseSuggestionsFromAI(
            response: String,
            screenState: ScreenState
    ): List<Suggestion> {
        return try {
            val jsonString = extractJsonArrayFromResponse(response) ?: return emptyList()
            val jsonArray = JsonParser.parseString(jsonString).asJsonArray

            jsonArray.mapIndexedNotNull { index, element ->
                val obj = element.asJsonObject
                val title = obj.get("title")?.asString ?: return@mapIndexedNotNull null
                val description = obj.get("description")?.asString ?: ""
                val type = obj.get("type")?.asString ?: "info"
                val target = obj.get("target")?.asString

                Suggestion(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        description = description,
                        action = createActionFromType(type, target, screenState),
                        icon = getIconFromType(type),
                        priority = jsonArray.size() - index
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AI suggestions", e)
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
            for (i in arrayStart until trimmed.length) {
                when (trimmed[i]) {
                    '[' -> bracketCount++
                    ']' -> {
                        bracketCount--
                        if (bracketCount == 0) {
                            endIndex = i
                            break
                        }
                    }
                }
            }
            if (endIndex > arrayStart) {
                return trimmed.substring(arrayStart, endIndex + 1)
            }
        }
        return null
    }

    private fun createActionFromType(
            type: String,
            target: String?,
            screenState: ScreenState
    ): AIAction? {
        return when (type.lowercase()) {
            "click" -> target?.let { AIAction.Click(it) }
            "type" -> target?.let { AIAction.Type(it, "") }
            "scroll" -> AIAction.Scroll(AIAction.ScrollDirection.DOWN)
            "navigate", "back" -> AIAction.Back
            "app" -> target?.let { AIAction.OpenApp(it) }
            else -> null
        }
    }

    private fun getIconFromType(type: String): Suggestion.SuggestionIcon {
        return when (type.lowercase()) {
            "click" -> Suggestion.SuggestionIcon.CLICK
            "type" -> Suggestion.SuggestionIcon.TYPE
            "scroll" -> Suggestion.SuggestionIcon.SCROLL
            "navigate", "back" -> Suggestion.SuggestionIcon.NAVIGATE
            "app" -> Suggestion.SuggestionIcon.APP
            "search" -> Suggestion.SuggestionIcon.SEARCH
            "share" -> Suggestion.SuggestionIcon.SHARE
            "copy" -> Suggestion.SuggestionIcon.COPY
            "info" -> Suggestion.SuggestionIcon.INFO
            else -> Suggestion.SuggestionIcon.LIGHTBULB
        }
    }

    /** Generate smart suggestions based on screen analysis (fallback/demo mode) */
    private fun generateSmartSuggestions(
            screenState: ScreenState,
            screenContext: String,
            maxSuggestions: Int
    ): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        val elements = screenState.flattenElements()
        val packageName = screenState.packageName.lowercase()

        // Analyze clickable elements
        val clickableElements = elements.filter { it.isClickable && it.getLabel() != null }
        val editableElements = elements.filter { it.isEditable }
        val scrollableElements = elements.filter { it.isScrollable }

        // App-specific suggestions
        when {
            packageName.contains("settings") -> {
                suggestions.add(
                        createSuggestion(
                                "Search Settings",
                                "Quickly find what you're looking for",
                                Suggestion.SuggestionIcon.SEARCH,
                                AIAction.Click("Search")
                        )
                )
                suggestions.add(
                        createSuggestion(
                                "Go Back",
                                "Return to previous screen",
                                Suggestion.SuggestionIcon.NAVIGATE,
                                AIAction.Back
                        )
                )
            }
            packageName.contains("chrome") || packageName.contains("browser") -> {
                suggestions.add(
                        createSuggestion(
                                "Search or Enter URL",
                                "Type in the address bar",
                                Suggestion.SuggestionIcon.TYPE,
                                AIAction.Click("Search or type URL")
                        )
                )
                suggestions.add(
                        createSuggestion(
                                "Open New Tab",
                                "Start a new browsing session",
                                Suggestion.SuggestionIcon.APP,
                                AIAction.Click("New tab")
                        )
                )
            }
            packageName.contains("message") || packageName.contains("sms") -> {
                suggestions.add(
                        createSuggestion(
                                "Start New Message",
                                "Compose a new text message",
                                Suggestion.SuggestionIcon.TYPE,
                                AIAction.Click("Start chat")
                        )
                )
            }
            packageName.contains("launcher") || packageName.contains("home") -> {
                suggestions.add(
                        createSuggestion(
                                "Open Settings",
                                "Adjust device preferences",
                                Suggestion.SuggestionIcon.APP,
                                AIAction.OpenApp("Settings")
                        )
                )
                suggestions.add(
                        createSuggestion(
                                "Open Browser",
                                "Browse the web",
                                Suggestion.SuggestionIcon.APP,
                                AIAction.OpenApp("Chrome")
                        )
                )
            }
        }

        // Add suggestions based on visible elements
        if (editableElements.isNotEmpty()) {
            val editField = editableElements.first()
            suggestions.add(
                    createSuggestion(
                            "Enter Text",
                            "Type in the ${editField.getLabel() ?: "text field"}",
                            Suggestion.SuggestionIcon.TYPE,
                            AIAction.Click(editField.getLabel() ?: "text field")
                    )
            )
        }

        // Suggest prominent clickable elements
        clickableElements
                .filter { it.text?.isNotBlank() == true && it.text!!.length in 2..30 }
                .take(2)
                .forEach { element ->
                    suggestions.add(
                            createSuggestion(
                                    "Tap \"${element.text}\"",
                                    "Click this button or link",
                                    Suggestion.SuggestionIcon.CLICK,
                                    AIAction.Click(element.text!!)
                            )
                    )
                }

        // Scrollable content
        if (scrollableElements.isNotEmpty()) {
            suggestions.add(
                    createSuggestion(
                            "Scroll Down",
                            "See more content below",
                            Suggestion.SuggestionIcon.SCROLL,
                            AIAction.Scroll(AIAction.ScrollDirection.DOWN)
                    )
            )
        }

        // Always add navigation option
        if (suggestions.none { it.icon == Suggestion.SuggestionIcon.NAVIGATE }) {
            suggestions.add(
                    createSuggestion(
                            "Go Back",
                            "Return to previous screen",
                            Suggestion.SuggestionIcon.NAVIGATE,
                            AIAction.Back
                    )
            )
        }

        return suggestions.distinctBy { it.title }.take(maxSuggestions).mapIndexed {
                index,
                suggestion ->
            suggestion.copy(priority = maxSuggestions - index)
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
                """{"thought":"User needs help","response":"I'm Omni, your on-device AI assistant! I can:\n\n• **See** what's on your screen\n• **Click** buttons and links\n• **Type** text in fields\n• **Scroll** through content\n• **Open apps** - try: Settings, Chrome, Messages, Phone, Camera, Clock\n• **Navigate** - go back, go home\n• **Remember** things you tell me\n\nTry saying:\n• \"open settings\"\n• \"what's on my screen?\"\n• \"scroll down\"\n• \"go back\"","actions":[],"complete":true}"""
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
}
