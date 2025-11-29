package com.example.omni_link.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni_link.ai.CactusLLMProvider
import com.example.omni_link.ai.ChatMessage
import com.example.omni_link.ai.LLMProvider
import com.example.omni_link.ai.MemoryItem
import com.example.omni_link.ai.ModelManager
import com.example.omni_link.ai.OpenRouterProvider
import com.example.omni_link.ai.SemanticRAGEngine
import com.example.omni_link.data.AIAction
import com.example.omni_link.data.ActionResult
import com.example.omni_link.data.FocusAreaSelectionState
import com.example.omni_link.data.FocusRegion
import com.example.omni_link.data.ScreenState
import com.example.omni_link.data.Suggestion
import com.example.omni_link.data.SuggestionState
import com.example.omni_link.data.db.MemoryRepository
import com.example.omni_link.data.db.OmniLinkDatabase
import com.example.omni_link.debug.DebugLogManager
import com.example.omni_link.glyph.GlyphType
import com.example.omni_link.service.OmniAccessibilityService
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** ViewModel for the NOMM (Nothing On My Mind) AI Assistant */
class OmniViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "OmniViewModel"
        private const val DEFAULT_MODEL = "lfm2-350m" // LFM2 350M - fast & efficient

        // SharedPreferences keys for settings
        private const val PREFS_NAME = "omnilink_settings"
        private const val KEY_OPENROUTER_MODEL = "openrouter_model"
    }

    // SharedPreferences for persisting settings
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Dependencies
    private val database = OmniLinkDatabase.getInstance(application)
    private val memoryRepository = MemoryRepository(database)
    val modelManager = ModelManager(application)
    // Share the CactusLM instance from ModelManager so provider knows about downloaded models
    private val llmProvider: LLMProvider = CactusLLMProvider(application, modelManager.cactusLM)

    // Semantic RAG Engine - uses Cactus SDK native embeddings for smart recall
    private val semanticRAG = SemanticRAGEngine(database, modelManager.cactusLM)

    // Current session
    private val sessionId = UUID.randomUUID().toString()

    // UI State
    private val _uiState = MutableStateFlow(OmniUiState())
    val uiState: StateFlow<OmniUiState> = _uiState.asStateFlow()

    // Model download state (legacy single download)
    private val _downloadState =
            MutableStateFlow<ModelManager.DownloadState>(ModelManager.DownloadState.Idle)
    val downloadState: StateFlow<ModelManager.DownloadState> = _downloadState.asStateFlow()

    // Per-model download states for simultaneous downloads
    val activeDownloads: StateFlow<Map<String, ModelManager.ModelDownloadState>> =
            modelManager.activeDownloads

    // Messages
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Cactus example models (for UI)
    val cactusModels: StateFlow<List<ModelManager.CactusModelInfo>> = modelManager.cactusModels

    // Screen state from accessibility service
    val screenState: StateFlow<ScreenState?> = OmniAccessibilityService.screenState
    val isServiceRunning: StateFlow<Boolean> = OmniAccessibilityService.isRunning

    // Suggestion overlay state
    private val _suggestionState = MutableStateFlow(SuggestionState())
    val suggestionState: StateFlow<SuggestionState> = _suggestionState.asStateFlow()

    // Glyph type selection
    private val _currentGlyphType = MutableStateFlow(GlyphType.MOBIUS_FIGURE_8)
    val currentGlyphType: StateFlow<GlyphType> = _currentGlyphType.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════════
    // OPENROUTER CLOUD PROVIDER (for Fast Forward)
    // ═══════════════════════════════════════════════════════════════════════════

    // Load saved OpenRouter model
    private fun loadSavedOpenRouterModel(): OpenRouterProvider.Model {
        val savedModelId = prefs.getString(KEY_OPENROUTER_MODEL, null)
        return if (savedModelId != null) {
            OpenRouterProvider.Model.fromId(savedModelId) ?: OpenRouterProvider.Model.getDefault()
        } else {
            OpenRouterProvider.Model.getDefault()
        }
    }

    // OpenRouter model selection (persisted)
    private val _currentOpenRouterModel =
            MutableStateFlow(
                    loadSavedOpenRouterModel().also {
                        // Sync with OpenRouterProvider
                        OpenRouterProvider.setModel(it)
                    }
            )
    val currentOpenRouterModel: StateFlow<OpenRouterProvider.Model> =
            _currentOpenRouterModel.asStateFlow()

    /** Check if OpenRouter is available (API key configured) */
    fun isOpenRouterAvailable(): Boolean = OpenRouterProvider.isAvailable()

    /** Set the OpenRouter model (persisted) */
    fun setOpenRouterModel(model: OpenRouterProvider.Model) {
        OpenRouterProvider.setModel(model)
        _currentOpenRouterModel.value = model
        prefs.edit().putString(KEY_OPENROUTER_MODEL, model.id).apply()
        Log.d(TAG, "OpenRouter model changed to: ${model.displayName} (saved)")
    }

    /** Get available OpenRouter models */
    fun getOpenRouterModels(): List<OpenRouterProvider.Model> =
            OpenRouterProvider.getAvailableModels()

    // Track current suggestion generation job to cancel on new requests (prevents race conditions)
    private var suggestionGenerationJob: Job? = null

    init {
        // Refresh catalog and attempt to auto-load a downloaded model
        viewModelScope.launch {
            modelManager.refreshCactusModels()
            initializeModel()
        }

        // Load conversation history
        viewModelScope.launch {
            memoryRepository.getSessionMessages(sessionId).collect { entities ->
                _messages.value =
                        entities.map { entity ->
                            ChatMessage(
                                    role = ChatMessage.Role.valueOf(entity.role),
                                    content = entity.content,
                                    timestamp = entity.timestamp
                            )
                        }
            }
        }

        // Set up overlay callbacks
        setupOverlayCallbacks()

        // Sync suggestion state with accessibility service
        viewModelScope.launch {
            _suggestionState.collect { state ->
                OmniAccessibilityService.instance?.updateSuggestionState(state)
            }
        }
    }

    /** Setup callbacks for overlay interactions */
    private fun setupOverlayCallbacks() {
        OmniAccessibilityService.onSuggestionButtonClicked = { showSuggestions() }
        OmniAccessibilityService.onSuggestionClicked = { suggestion ->
            executeSuggestion(suggestion)
        }
        OmniAccessibilityService.onDismissSuggestions = { hideSuggestions() }

        // Focus area callbacks
        OmniAccessibilityService.onFocusAreaSelectionStart = { x, y ->
            onFocusAreaSelectionStart(x, y)
        }
        OmniAccessibilityService.onFocusAreaSelectionUpdate = { x, y ->
            onFocusAreaSelectionUpdate(x, y)
        }
        OmniAccessibilityService.onFocusAreaSelectionEnd = { onFocusAreaSelectionEnd() }
        OmniAccessibilityService.onFocusAreaClear = { clearFocusRegion() }
        OmniAccessibilityService.onFocusAreaConfirm = { confirmFocusArea() }

        // Text selection callbacks (Circle-to-Search)
        OmniAccessibilityService.onGenerateTextOptions = { generateTextOptions() }
        OmniAccessibilityService.onTextOptionSelected = { option -> executeTextOption(option) }

        // Fast forward callback (cloud AI inference - OpenRouter/Gemini)
        OmniAccessibilityService.onFastForward = { fastForwardSuggestions() }
    }

    /**
     * Initialize the AI model from Cactus catalog if available, otherwise legacy GGUF, otherwise
     * demo mode
     */
    private suspend fun initializeModel() {
        _uiState.update { it.copy(isLoading = true, statusMessage = "Checking for AI model...") }

        // 1) Prefer Cactus catalog if any model is already downloaded
        val catalog = cactusModels.value
        val downloadedSlug = catalog.firstOrNull { it.isDownloaded }?.slug
        if (downloadedSlug != null) {
            Log.d(TAG, "Found downloaded model in catalog: $downloadedSlug")
            _uiState.update { it.copy(statusMessage = "Loading $downloadedSlug...") }
            val result = llmProvider.loadModel(downloadedSlug)
            if (result.isSuccess) {
                Log.d(TAG, "Model loaded successfully: $downloadedSlug")
                // Initialize semantic embeddings for RAG
                initializeSemanticEmbeddings()
                _uiState.update {
                    it.copy(
                            isLoading = false,
                            isModelReady = true,
                            currentModel = downloadedSlug,
                            statusMessage = "AI Ready ($downloadedSlug)"
                    )
                }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                Log.e(TAG, "Failed to load model $downloadedSlug: $error")
                _uiState.update {
                    it.copy(
                            isLoading = false,
                            isModelReady = false,
                            currentModel = null,
                            statusMessage = "Load failed: $error"
                    )
                }
            }
            return
        }

        // 2) Legacy GGUF fallback
        val downloadedModels = modelManager.getDownloadedModels()
        if (downloadedModels.isNotEmpty()) {
            val modelFile = downloadedModels.first()
            Log.d(TAG, "Found legacy GGUF model: ${modelFile.name}")
            _uiState.update { it.copy(statusMessage = "Loading ${modelFile.name}...") }
            val result = llmProvider.loadModel(modelFile.absolutePath)
            if (result.isSuccess) {
                // Initialize semantic embeddings for RAG
                initializeSemanticEmbeddings()
                _uiState.update {
                    it.copy(
                            isLoading = false,
                            isModelReady = true,
                            currentModel = modelFile.nameWithoutExtension,
                            statusMessage = "AI Ready (${modelFile.nameWithoutExtension})"
                    )
                }
            } else {
                Log.e(TAG, "Failed to load legacy model: ${result.exceptionOrNull()?.message}")
                _uiState.update {
                    it.copy(
                            isLoading = false,
                            isModelReady = false,
                            currentModel = null,
                            statusMessage = "Load failed: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
            return
        }

        Log.d(TAG, "No models found")
        // 3) No model available
        _uiState.update {
            it.copy(
                    isLoading = false,
                    isModelReady = false,
                    currentModel = null,
                    statusMessage = "No model downloaded"
            )
        }

        addMessage(
                ChatMessage(
                        role = ChatMessage.Role.ASSISTANT,
                        content =
                                "👋 Hi! I'm NOMM (Nothing On My Mind), your on-device AI assistant.\n\n" +
                                        "**No model is loaded.** Please download a model from the Model Settings (brain icon) to start using AI.\n\n" +
                                        "📱 *Also make sure Accessibility Service is enabled!*"
                )
        )
    }

    /** Download a legacy GGUF model (kept for compatibility) */
    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            modelManager.downloadModel(modelId).collect { state ->
                _downloadState.value = state

                when (state) {
                    is ModelManager.DownloadState.Downloading -> {
                        _uiState.update {
                            it.copy(
                                    statusMessage =
                                            "Downloading... ${(state.progress * 100).toInt()}%"
                            )
                        }
                    }
                    is ModelManager.DownloadState.Completed -> {
                        _uiState.update { it.copy(statusMessage = "Loading model...") }
                        val result = llmProvider.loadModel(state.file.absolutePath)
                        _uiState.update {
                            it.copy(
                                    isModelReady = result.isSuccess,
                                    currentModel =
                                            if (result.isSuccess) state.file.nameWithoutExtension
                                            else null,
                                    statusMessage =
                                            if (result.isSuccess) "AI Ready" else "Load failed"
                            )
                        }
                        if (result.isSuccess) {
                            addMessage(
                                    ChatMessage(
                                            role = ChatMessage.Role.ASSISTANT,
                                            content =
                                                    "✅ Model loaded! I'm now running with full AI capabilities."
                                    )
                            )
                        }
                    }
                    is ModelManager.DownloadState.Failed -> {
                        _uiState.update {
                            it.copy(statusMessage = "Download failed: ${state.error}")
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    /** Download a Cactus SDK model (by slug) - supports simultaneous downloads */
    fun downloadCactusModel(slug: String, autoLoad: Boolean = true) {
        viewModelScope.launch {
            // Check if model is already downloaded - skip download if present
            val isAlreadyDownloaded = cactusModels.value.firstOrNull { it.slug == slug }?.isDownloaded == true
            if (isAlreadyDownloaded) {
                Log.d(TAG, "Model $slug is already downloaded, skipping download")
                // If autoLoad is requested, just load the existing model
                if (autoLoad && !uiState.value.isModelReady) {
                    selectCactusModel(slug)
                }
                return@launch
            }

            modelManager.downloadCactusModel(slug).collect { state ->
                // Only update global state if this is the first/only download
                val activeCount = activeDownloads.value.count { it.value.isDownloading }
                if (activeCount <= 1) {
                    _downloadState.value = state
                }

                when (state) {
                    is ModelManager.DownloadState.Downloading -> {
                        // Status shows only if no model is ready yet
                        if (!uiState.value.isModelReady) {
                            val pct = (state.progress * 100).toInt()
                            _uiState.update {
                                it.copy(statusMessage = "Downloading $slug... $pct%")
                            }
                        }
                    }
                    is ModelManager.DownloadState.CompletedCactus -> {
                        // Give SDK time to register the downloaded model
                        delay(1000)
                        // Refresh catalog to ensure model is recognized
                        modelManager.refreshCactusModels()

                        // Log catalog state before loading
                        Log.d(TAG, "Catalog after download - attempting to load: $slug")
                        cactusModels.value.forEach { model ->
                            Log.d(TAG, "  Model: ${model.slug}, downloaded=${model.isDownloaded}")
                        }

                        // Auto-load if requested AND no model is currently loaded
                        if (autoLoad && !uiState.value.isModelReady) {
                            _uiState.update { it.copy(statusMessage = "Loading $slug...") }
                            val result = llmProvider.loadModel(slug)
                            if (result.isSuccess) {
                                _uiState.update {
                                    it.copy(
                                            isModelReady = true,
                                            currentModel = slug,
                                            statusMessage = "AI Ready ($slug)"
                                    )
                                }
                                addMessage(
                                        ChatMessage(
                                                role = ChatMessage.Role.ASSISTANT,
                                                content =
                                                        "✅ $slug loaded! I'm now running with full AI capabilities."
                                        )
                                )
                            } else {
                                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                                Log.e(TAG, "Model load failed: $errorMsg")
                                _uiState.update {
                                    it.copy(
                                            statusMessage =
                                                    "Download complete. Load failed: $errorMsg"
                                    )
                                }
                            }
                        } else {
                            // Just update status that download completed
                            Log.d(
                                    TAG,
                                    "Model $slug downloaded (auto-load=${autoLoad}, modelReady=${uiState.value.isModelReady})"
                            )
                        }

                        // Clear download state after a delay to allow UI to show completion
                        delay(2000)
                        modelManager.clearDownloadState(slug)
                    }
                    is ModelManager.DownloadState.Failed -> {
                        if (!uiState.value.isModelReady) {
                            _uiState.update {
                                it.copy(statusMessage = "Download failed: ${state.error}")
                            }
                        }
                        addMessage(
                                ChatMessage(
                                        role = ChatMessage.Role.ASSISTANT,
                                        content = "❌ Download of $slug failed: ${state.error}"
                                )
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    /** Clear download state for a model (e.g., after error acknowledged) */
    fun clearModelDownloadState(slug: String) {
        modelManager.clearDownloadState(slug)
    }

    /** Select a previously downloaded Cactus model by slug */
    fun selectCactusModel(slug: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Loading $slug...") }
            // Refresh catalog first to ensure accurate state
            modelManager.refreshCactusModels()

            val result = llmProvider.loadModel(slug)
            if (result.isSuccess) {
                // Initialize semantic embeddings for RAG
                initializeSemanticEmbeddings()
                _uiState.update {
                    it.copy(
                            isModelReady = true,
                            currentModel = slug,
                            statusMessage = "AI Ready ($slug)"
                    )
                }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                Log.e(TAG, "Select model failed: $errorMsg")
                _uiState.update {
                    it.copy(
                            isModelReady = false,
                            currentModel = null,
                            statusMessage = "Load failed: $errorMsg"
                    )
                }
            }
        }
    }

    /** Send a message to the AI assistant */
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            // Add user message
            val userMessage = ChatMessage(role = ChatMessage.Role.USER, content = text)
            addMessage(userMessage)
            memoryRepository.saveMessage("USER", text, sessionId)

            // Check if this is a recall/memory search query
            if (isRecallQuery(text)) {
                _uiState.update { it.copy(isThinking = true) }
                try {
                    handleSmartRecall(text)
                } finally {
                    _uiState.update { it.copy(isThinking = false) }
                }
                return@launch
            }

            // Show thinking state
            _uiState.update { it.copy(isThinking = true) }

            try {
                // Get current screen state
                val currentScreen = OmniAccessibilityService.instance?.captureScreen()

                // Get relevant memories
                val memories =
                        memoryRepository.getContextMemories(10).map { entity ->
                            MemoryItem(
                                    key = entity.key,
                                    value = entity.value,
                                    category = entity.category,
                                    timestamp = entity.updatedAt
                            )
                        }

                // Generate AI response
                val response =
                        llmProvider.generateResponse(
                                userMessage = text,
                                screenState = currentScreen,
                                conversationHistory = _messages.value,
                                memory = memories
                        )

                if (response.isSuccess) {
                    val llmResponse = response.getOrThrow()

                    // Execute actions if any
                    llmResponse.actions?.let { plan -> executeActionPlan(plan.actions) }

                    // Save memory updates
                    llmResponse.memoryUpdates.forEach { memory ->
                        memoryRepository.remember(
                                key = memory.key,
                                value = memory.value,
                                category = memory.category
                        )
                    }

                    // Add assistant response (extract from first Respond action or use raw text)
                    val responseText =
                            llmResponse
                                    .actions
                                    ?.actions
                                    ?.filterIsInstance<AIAction.Respond>()
                                    ?.firstOrNull()
                                    ?.message
                                    ?: llmResponse.text

                    val assistantMessage =
                            ChatMessage(role = ChatMessage.Role.ASSISTANT, content = responseText)
                    addMessage(assistantMessage)
                    memoryRepository.saveMessage("ASSISTANT", responseText, sessionId)

                    // Update stats
                    _uiState.update {
                        it.copy(
                                lastInferenceTimeMs = llmResponse.inferenceTimeMs,
                                totalTokensUsed = it.totalTokensUsed + llmResponse.tokensUsed
                        )
                    }
                } else {
                    // Error response
                    val errorMessage =
                            ChatMessage(
                                    role = ChatMessage.Role.ASSISTANT,
                                    content =
                                            "Sorry, I encountered an error: ${response.exceptionOrNull()?.message}"
                            )
                    addMessage(errorMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message", e)
                val errorMessage =
                        ChatMessage(
                                role = ChatMessage.Role.ASSISTANT,
                                content = "Sorry, something went wrong. Please try again."
                        )
                addMessage(errorMessage)
            } finally {
                _uiState.update { it.copy(isThinking = false) }
            }
        }
    }

    /** Execute a list of AI actions */
    private suspend fun executeActionPlan(actions: List<AIAction>) {
        val service = OmniAccessibilityService.instance ?: return

        for (action in actions) {
            // Skip Respond/Clarify/Complete as they're just messages
            if (action is AIAction.Respond ||
                            action is AIAction.Clarify ||
                            action is AIAction.Complete
            ) {
                continue
            }

            _uiState.update { it.copy(currentAction = describeAction(action)) }

            val result = service.executeAction(action)

            when (result) {
                is ActionResult.Success -> {
                    Log.d(TAG, "Action succeeded: ${result.description}")
                }
                is ActionResult.Failure -> {
                    Log.e(TAG, "Action failed: ${result.reason}")
                    // Could add error message to chat
                }
                is ActionResult.NeedsConfirmation -> {
                    // TODO: Implement confirmation flow
                    Log.w(TAG, "Action needs confirmation: ${result.reason}")
                }
            }

            // Small delay between actions for stability
            delay(300)
        }

        _uiState.update { it.copy(currentAction = null) }
    }

    private fun describeAction(action: AIAction): String {
        return when (action) {
            is AIAction.Click -> "Clicking '${action.target}'..."
            is AIAction.Type -> "Typing '${action.text}'..."
            is AIAction.Scroll -> "Scrolling ${action.direction.name.lowercase()}..."
            is AIAction.Back -> "Going back..."
            is AIAction.Home -> "Going home..."
            is AIAction.OpenApp -> "Opening ${action.appName}..."
            is AIAction.Wait -> "Waiting..."
            // Device Intent Actions
            is AIAction.OpenCalendar ->
                    if (action.title != null) "Creating event '${action.title}'..."
                    else "Opening calendar..."
            is AIAction.DialNumber -> "Opening dialer..."
            is AIAction.CallNumber -> "Calling ${action.phoneNumber}..."
            is AIAction.SendSMS -> "Composing SMS..."
            is AIAction.OpenURL -> "Opening URL..."
            is AIAction.WebSearch -> "Searching the web..."
            is AIAction.SetAlarm -> "Setting alarm..."
            is AIAction.SetTimer -> "Setting timer..."
            is AIAction.ShareText -> "Opening share..."
            is AIAction.CopyToClipboard -> "Copying to clipboard..."
            is AIAction.SendEmail -> "Composing email..."
            is AIAction.OpenMaps ->
                    if (action.navigate) "Starting navigation..." else "Opening maps..."
            is AIAction.PlayMedia -> "Playing media..."
            is AIAction.OpenSettings -> "Opening ${action.section.name.lowercase()} settings..."
            else -> "Processing..."
        }
    }

    private fun addMessage(message: ChatMessage) {
        _messages.update { it + message }
    }

    /** Clear conversation history */
    fun clearConversation() {
        viewModelScope.launch {
            memoryRepository.saveMessage("SYSTEM", "Conversation cleared", sessionId)
            _messages.value = emptyList()
        }
    }

    /** Manual screen capture trigger */
    fun refreshScreen() {
        OmniAccessibilityService.instance?.captureScreen()
    }

    /** Remember something explicitly */
    fun remember(key: String, value: String) {
        viewModelScope.launch { memoryRepository.remember(key, value, "user_provided") }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SEMANTIC RAG - SMART MEMORY RECALL
    // ═══════════════════════════════════════════════════════════════════════════

    /** Initialize semantic embeddings after model is loaded */
    private suspend fun initializeSemanticEmbeddings() {
        try {
            val embeddingsReady = semanticRAG.initializeEmbeddings()
            if (embeddingsReady) {
                Log.d(TAG, "Semantic embeddings initialized - native RAG enabled")
            } else {
                Log.d(TAG, "Semantic embeddings not available - using TF-IDF fallback")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize semantic embeddings", e)
        }
    }

    /**
     * Detect if a query is a recall/memory search command. Examples:
     * - "recall: what was that recipe?"
     * - "find: vacation planning stuff"
     * - "search history: meeting notes"
     * - "what did I copy about..."
     * - "find similar to..."
     */
    private fun isRecallQuery(text: String): Boolean {
        val lowerText = text.lowercase().trim()
        return lowerText.startsWith("recall:") ||
                lowerText.startsWith("recall ") ||
                lowerText.startsWith("find:") ||
                lowerText.startsWith("search history:") ||
                lowerText.startsWith("search history ") ||
                lowerText.contains("what did i copy") ||
                lowerText.contains("what did i search") ||
                lowerText.contains("what was that") ||
                lowerText.contains("find similar") ||
                lowerText.contains("what was i looking at") ||
                lowerText.contains("what did i do") ||
                (lowerText.contains("remember") && lowerText.contains("?"))
    }

    /** Extract the actual query from a recall command. */
    private fun extractRecallQuery(text: String): String {
        val lowerText = text.lowercase().trim()
        return when {
            lowerText.startsWith("recall:") -> text.substringAfter("recall:").trim()
            lowerText.startsWith("recall ") -> text.substringAfter("recall ").trim()
            lowerText.startsWith("find:") -> text.substringAfter("find:").trim()
            lowerText.startsWith("search history:") -> text.substringAfter("search history:").trim()
            lowerText.startsWith("search history ") -> text.substringAfter("search history ").trim()
            else -> text
        }
    }

    /**
     * Handle a smart recall query using semantic search. Uses Cactus SDK native embeddings for
     * semantic similarity.
     */
    private suspend fun handleSmartRecall(query: String) {
        try {
            val recallQuery = extractRecallQuery(query)
            Log.d(TAG, "Smart recall query: $recallQuery")

            val result = semanticRAG.smartRecall(recallQuery)

            val searchMethod =
                    if (result.usedSemanticSearch) "🧠 Semantic Search" else "📝 Keyword Search"
            val responseText = buildString {
                appendLine("**$searchMethod Results**\n")
                appendLine(result.summary)
                if (result.usedSemanticSearch) {
                    appendLine("\n---")
                    appendLine("_Using native Cactus SDK embeddings for semantic matching_")
                }
            }

            val assistantMessage =
                    ChatMessage(role = ChatMessage.Role.ASSISTANT, content = responseText)
            addMessage(assistantMessage)
            memoryRepository.saveMessage("ASSISTANT", responseText, sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Smart recall error", e)
            val errorMessage =
                    ChatMessage(
                            role = ChatMessage.Role.ASSISTANT,
                            content = "Sorry, I couldn't search your history: ${e.message}"
                    )
            addMessage(errorMessage)
        }
    }

    /**
     * Public method to perform smart memory recall from the chat. Use cases:
     * - "recall: what was that cooking website?"
     * - "find: things about my vacation"
     * - "search history: meeting notes"
     */
    fun smartRecall(query: String) {
        viewModelScope.launch {
            // Add user message
            val userMessage = ChatMessage(role = ChatMessage.Role.USER, content = query)
            addMessage(userMessage)
            memoryRepository.saveMessage("USER", query, sessionId)

            _uiState.update { it.copy(isThinking = true) }
            try {
                handleSmartRecall(query)
            } finally {
                _uiState.update { it.copy(isThinking = false) }
            }
        }
    }

    /** Find content similar to the given text using semantic embeddings. */
    fun findSimilar(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isThinking = true) }
            try {
                val similar = semanticRAG.findSimilar(text, limit = 8)

                val responseText =
                        if (similar.isEmpty()) {
                            "I couldn't find anything similar to that in your history."
                        } else {
                            buildString {
                                appendLine("**🔍 Similar Content Found:**\n")
                                for (item in similar) {
                                    val similarity = (item.similarity * 100).toInt()
                                    appendLine("• **${item.title}** ($similarity% match)")
                                    appendLine(
                                            "  ${item.preview.take(100)}${if (item.preview.length > 100) "..." else ""}"
                                    )
                                    appendLine()
                                }
                            }
                        }

                addMessage(ChatMessage(role = ChatMessage.Role.ASSISTANT, content = responseText))
            } catch (e: Exception) {
                Log.e(TAG, "Find similar error", e)
                addMessage(
                        ChatMessage(
                                role = ChatMessage.Role.ASSISTANT,
                                content =
                                        "Sorry, I couldn't search for similar content: ${e.message}"
                        )
                )
            } finally {
                _uiState.update { it.copy(isThinking = false) }
            }
        }
    }

    /** Get embedding cache statistics for debugging. */
    suspend fun getEmbeddingStats(): String {
        val stats = semanticRAG.getCacheStats()
        return "Embeddings: ${if (stats.embeddingsAvailable) "Active" else "Inactive"}, Cache: ${stats.size}/${stats.maxSize}"
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SUGGESTION OVERLAY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Generate suggestions based on current screen context with streaming output */
    fun generateSuggestions() {
        // Cancel any previous suggestion generation to prevent race conditions in native code
        suggestionGenerationJob?.cancel()

        suggestionGenerationJob =
                viewModelScope.launch {
                    if (!uiState.value.isModelReady) {
                        _suggestionState.update {
                            it.copy(
                                    isVisible = true,
                                    isLoading = false,
                                    error = "No AI model loaded. Please download a model first.",
                                    suggestions = emptyList(),
                                    streamingText = "",
                                    isStreaming = false,
                                    canUseFastForward = true // Gemini is always available
                            )
                        }
                        return@launch
                    }

                    val currentScreen = OmniAccessibilityService.instance?.captureScreen()
                    if (currentScreen == null) {
                        _suggestionState.update {
                            it.copy(
                                    isVisible = true,
                                    isLoading = false,
                                    error =
                                            "Cannot capture screen. Make sure accessibility service is enabled.",
                                    suggestions = emptyList(),
                                    streamingText = "",
                                    isStreaming = false,
                                    canUseFastForward = true
                            )
                        }
                        return@launch
                    }

                    // Get the focus region if set
                    val focusRegion = _suggestionState.value.focusRegion
                    val screenContext =
                            if (focusRegion != null) {
                                currentScreen.toPromptContextWithFocus(focusRegion)
                            } else {
                                currentScreen.toPromptContext()
                            }

                    // Show loading state with streaming enabled
                    // Store the screen state so fast forward can use the same context
                    _suggestionState.update {
                        it.copy(
                                isVisible = true,
                                isLoading = true,
                                isStreaming = true,
                                streamingText = "",
                                error = null,
                                lastScreenContext = screenContext,
                                lastScreenState = currentScreen, // Save for fast forward to use
                                isCloudInferenceActive = false,
                                canUseFastForward = true // Fast forward always available
                        )
                    }

                    try {
                        // Use streaming generation with focus region awareness (local inference)
                        llmProvider.generateSuggestionsStreaming(
                                        currentScreen,
                                        maxSuggestions = 5,
                                        focusRegion = focusRegion
                                )
                                .collect { event ->
                                    when (event) {
                                        is com.example.omni_link.ai.SuggestionStreamEvent.Token -> {
                                            // Update streaming text as tokens arrive
                                            _suggestionState.update {
                                                it.copy(streamingText = event.fullText)
                                            }
                                        }
                                        is com.example.omni_link.ai.SuggestionStreamEvent.Complete -> {
                                            // Generation complete
                                            Log.d(
                                                    TAG,
                                                    "Generated ${event.suggestions.size} suggestions via 📱 local"
                                            )
                                            _suggestionState.update {
                                                it.copy(
                                                        isLoading = false,
                                                        isStreaming = false,
                                                        isCloudInferenceActive = false,
                                                        suggestions =
                                                                event.suggestions
                                                                        .sortedByDescending { s ->
                                                                            s.priority
                                                                        },
                                                        error = null,
                                                        streamingText = "",
                                                        canUseFastForward = true
                                                )
                                            }
                                        }
                                        is com.example.omni_link.ai.SuggestionStreamEvent.Error -> {
                                            Log.e(
                                                    TAG,
                                                    "Suggestion generation failed: ${event.message}"
                                            )
                                            _suggestionState.update {
                                                it.copy(
                                                        isLoading = false,
                                                        isStreaming = false,
                                                        isCloudInferenceActive = false,
                                                        error = event.message,
                                                        suggestions = emptyList(),
                                                        streamingText = "",
                                                        canUseFastForward = true
                                                )
                                            }
                                        }
                                    }
                                }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error generating suggestions", e)
                        _suggestionState.update {
                            it.copy(
                                    isLoading = false,
                                    isStreaming = false,
                                    isCloudInferenceActive = false,
                                    error = e.message ?: "Failed to generate suggestions",
                                    suggestions = emptyList(),
                                    streamingText = "",
                                    canUseFastForward = true
                            )
                        }
                    }
                }
    }

    /** Fast forward: Use OpenRouter cloud AI for ultra-fast inference */
    fun fastForwardSuggestions() {
        // Cancel any previous suggestion generation
        suggestionGenerationJob?.cancel()

        suggestionGenerationJob =
                viewModelScope.launch {
                    val model = _currentOpenRouterModel.value
                    val providerName = "OpenRouter (${model.displayName})"

                    Log.d(TAG, "⚡ Fast forward triggered - using $providerName")
                    DebugLogManager.info(
                            TAG,
                            "Fast Forward 🌐",
                            "Using $providerName for instant results"
                    )

                    // Use the stored screen state from local inference instead of re-capturing
                    // This ensures fast forward analyzes the SAME screen as local inference
                    val storedScreen = _suggestionState.value.lastScreenState
                    val currentScreen =
                            storedScreen ?: OmniAccessibilityService.instance?.captureScreen()

                    // Debug logging for screen capture
                    if (currentScreen != null) {
                        val elementCount = currentScreen.flattenElements().size
                        val sourceNote =
                                if (storedScreen != null)
                                        "(using stored screen from local inference)"
                                else "(freshly captured)"
                        Log.d(
                                TAG,
                                "⚡ Fast forward screen: ${currentScreen.packageName}, ${elementCount} elements $sourceNote"
                        )
                        DebugLogManager.info(
                                TAG,
                                "Fast Forward Screen Context",
                                "Source: ${if (storedScreen != null) "Stored from local inference" else "Freshly captured"}\n" +
                                        "App: ${currentScreen.packageName}\n" +
                                        "Activity: ${currentScreen.activityName ?: "unknown"}\n" +
                                        "Elements: $elementCount\n" +
                                        "Context preview: ${currentScreen.toPromptContext().take(300)}..."
                        )
                    }
                    if (currentScreen == null) {
                        _suggestionState.update {
                            it.copy(
                                    isLoading = false,
                                    isStreaming = false,
                                    isCloudInferenceActive = false,
                                    error = "Cannot capture screen",
                                    canUseFastForward = true
                            )
                        }
                        return@launch
                    }

                    val focusRegion = _suggestionState.value.focusRegion

                    // Show cloud loading state
                    _suggestionState.update {
                        it.copy(
                                isVisible = true,
                                isLoading = true,
                                isStreaming = false,
                                streamingText = "🌐 Fast Forward via $providerName...",
                                isCloudInferenceActive = true,
                                canUseFastForward = false,
                                error = null
                        )
                    }

                    try {
                        // Use OpenRouter for cloud inference
                        val suggestionsFlow =
                                OpenRouterProvider.generateSuggestionsStreaming(
                                        currentScreen,
                                        maxSuggestions = 5,
                                        focusRegion = focusRegion
                                )

                        suggestionsFlow.collect { event ->
                            when (event) {
                                is com.example.omni_link.ai.SuggestionStreamEvent.Token -> {
                                    _suggestionState.update {
                                        it.copy(streamingText = event.fullText)
                                    }
                                }
                                is com.example.omni_link.ai.SuggestionStreamEvent.Complete -> {
                                    Log.d(
                                            TAG,
                                            "🌐 Fast Forward complete: ${event.suggestions.size} suggestions"
                                    )
                                    _suggestionState.update {
                                        it.copy(
                                                isLoading = false,
                                                isStreaming = false,
                                                isCloudInferenceActive = false,
                                                suggestions =
                                                        event.suggestions.sortedByDescending { s ->
                                                            s.priority
                                                        },
                                                error = null,
                                                streamingText = "",
                                                canUseFastForward = true
                                        )
                                    }
                                }
                                is com.example.omni_link.ai.SuggestionStreamEvent.Error -> {
                                    Log.e(TAG, "Fast Forward failed: ${event.message}")
                                    _suggestionState.update {
                                        it.copy(
                                                isLoading = false,
                                                isStreaming = false,
                                                isCloudInferenceActive = false,
                                                error = "Cloud error: ${event.message}",
                                                streamingText = "",
                                                canUseFastForward = true
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Fast Forward error", e)
                        _suggestionState.update {
                            it.copy(
                                    isLoading = false,
                                    isStreaming = false,
                                    isCloudInferenceActive = false,
                                    error = "Cloud error: ${e.message}",
                                    streamingText = "",
                                    canUseFastForward = true
                            )
                        }
                    }
                }
    }

    /** Check if fast forward is available (requires OpenRouter API key) */
    fun isFastForwardAvailable(): Boolean = OpenRouterProvider.isAvailable()

    /** Execute a suggestion's action */
    fun executeSuggestion(suggestion: Suggestion) {
        viewModelScope.launch {
            val action = suggestion.action ?: return@launch
            val service = OmniAccessibilityService.instance ?: return@launch

            // Hide suggestions panel
            _suggestionState.update { it.copy(isVisible = false) }

            // Show action being executed
            _uiState.update { it.copy(currentAction = suggestion.title) }

            Log.d(TAG, "Executing suggestion: ${suggestion.title}")
            val result = service.executeAction(action)

            when (result) {
                is ActionResult.Success -> {
                    Log.d(TAG, "Suggestion executed successfully: ${result.description}")
                }
                is ActionResult.Failure -> {
                    Log.e(TAG, "Suggestion execution failed: ${result.reason}")
                }
                is ActionResult.NeedsConfirmation -> {
                    Log.w(TAG, "Action needs confirmation: ${result.reason}")
                }
            }

            // Clear action state
            delay(300)
            _uiState.update { it.copy(currentAction = null) }
        }
    }

    /** Show the suggestions panel */
    fun showSuggestions() {
        DebugLogManager.info(TAG, "Showing suggestions panel", "Triggered by floating button click")
        _suggestionState.update { it.copy(isVisible = true) }
        generateSuggestions()
    }

    /** Hide the suggestions panel */
    fun hideSuggestions() {
        _suggestionState.update { it.copy(isVisible = false) }
    }

    /** Toggle suggestions panel visibility */
    fun toggleSuggestions() {
        if (_suggestionState.value.isVisible) {
            hideSuggestions()
        } else {
            showSuggestions()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FOCUS AREA METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Enable focus area selection mode */
    fun enableFocusAreaMode() {
        DebugLogManager.info(
                TAG,
                "Focus area mode enabled",
                "User can now select an area of interest"
        )
        _suggestionState.update {
            it.copy(
                    isFocusAreaModeEnabled = true,
                    focusAreaSelectionState = FocusAreaSelectionState()
            )
        }
        OmniAccessibilityService.instance?.showFocusAreaSelector()
    }

    /** Disable focus area selection mode */
    fun disableFocusAreaMode() {
        _suggestionState.update {
            it.copy(
                    isFocusAreaModeEnabled = false,
                    focusAreaSelectionState = FocusAreaSelectionState()
            )
        }
        OmniAccessibilityService.instance?.hideFocusAreaSelector()
    }

    /** Called when user starts dragging to select a focus area */
    fun onFocusAreaSelectionStart(x: Float, y: Float) {
        _suggestionState.update {
            it.copy(
                    focusAreaSelectionState =
                            FocusAreaSelectionState(
                                    isSelecting = true,
                                    startX = x,
                                    startY = y,
                                    currentX = x,
                                    currentY = y
                            )
            )
        }
    }

    /** Called while user is dragging to update the selection rectangle */
    fun onFocusAreaSelectionUpdate(x: Float, y: Float) {
        _suggestionState.update {
            it.copy(
                    focusAreaSelectionState =
                            it.focusAreaSelectionState.copy(currentX = x, currentY = y)
            )
        }
    }

    /** Called when user finishes selecting a focus area - auto-triggers analysis */
    fun onFocusAreaSelectionEnd() {
        val state = _suggestionState.value.focusAreaSelectionState

        // Check if selection is large enough
        val width = kotlin.math.abs(state.currentX - state.startX).toInt()
        val height = kotlin.math.abs(state.currentY - state.startY).toInt()

        if (FocusRegion.isValidSize(width, height)) {
            val region =
                    FocusRegion.fromTouchCoordinates(
                            startX = state.startX,
                            startY = state.startY,
                            endX = state.currentX,
                            endY = state.currentY
                    )

            DebugLogManager.info(
                    TAG,
                    "Focus area selected",
                    "Bounds: ${region.bounds}\nSize: ${width}x${height}px"
            )

            _suggestionState.update {
                it.copy(
                        focusRegion = region,
                        focusAreaSelectionState =
                                it.focusAreaSelectionState.copy(
                                        isSelecting = false,
                                        currentRegion = region
                                ),
                        isFocusAreaModeEnabled = false
                )
            }

            // Auto-close selector and trigger analysis
            OmniAccessibilityService.instance?.hideFocusAreaSelector()

            // Small delay to let UI update, then show suggestions with focus
            viewModelScope.launch {
                delay(100)
                showSuggestions()
            }
        } else {
            // Selection too small - still close the selector but don't set focus
            DebugLogManager.info(TAG, "Focus area too small", "Min size: ${FocusRegion.MIN_SIZE}px")
            _suggestionState.update {
                it.copy(
                        focusAreaSelectionState =
                                it.focusAreaSelectionState.copy(isSelecting = false),
                        isFocusAreaModeEnabled = false
                )
            }

            // Close selector and go back to suggestions
            OmniAccessibilityService.instance?.hideFocusAreaSelector()
            viewModelScope.launch {
                delay(100)
                showSuggestions()
            }
        }
    }

    /** Clear the current focus region and re-analyze full screen */
    fun clearFocusRegion() {
        DebugLogManager.info(TAG, "Focus area cleared", "AI will analyze the full screen")
        _suggestionState.update {
            it.copy(focusRegion = null, focusAreaSelectionState = FocusAreaSelectionState())
        }
        // Re-generate suggestions for full screen
        generateSuggestions()
    }

    /** Cancel focus area selection and return to suggestions */
    fun confirmFocusArea() {
        // This is called when user taps cancel/dismiss without making a selection
        // Just close the selector and go back to suggestions
        _suggestionState.update {
            it.copy(
                    isFocusAreaModeEnabled = false,
                    focusAreaSelectionState = FocusAreaSelectionState()
            )
        }
        OmniAccessibilityService.instance?.hideFocusAreaSelector()

        // Show suggestions with existing focus (if any)
        viewModelScope.launch {
            delay(100)
            showSuggestions()
        }
    }

    /** Check if there's an active focus region */
    fun hasFocusRegion(): Boolean = _suggestionState.value.focusRegion != null

    /** Get the current focus region */
    fun getFocusRegion(): FocusRegion? = _suggestionState.value.focusRegion

    // ═══════════════════════════════════════════════════════════════════════════
    // TEXT SELECTION OPTIONS (Circle-to-Search)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Generate AI-powered options for the selected text from OCR */
    private fun generateTextOptions() {
        viewModelScope.launch {
            val service = OmniAccessibilityService.instance ?: return@launch
            val selectedText = OmniAccessibilityService.textSelectionState.value.getSelectedText()

            if (selectedText.isBlank()) {
                Log.w(TAG, "No text selected for option generation")
                return@launch
            }

            Log.d(TAG, "Generating options for text: ${selectedText.take(50)}...")
            DebugLogManager.info(TAG, "AI Options", "Generating for: ${selectedText.take(50)}...")

            // Update state to show loading
            service.updateTextSelectionState(
                    OmniAccessibilityService.textSelectionState.value.copy(
                            isGeneratingOptions = true,
                            optionsStreamingText = "",
                            textOptions = emptyList()
                    )
            )

            try {
                // Use the LLM provider to generate options with streaming
                llmProvider.generateTextOptions(selectedText, maxOptions = 6).collect { event ->
                    when (event) {
                        is com.example.omni_link.ai.TextOptionStreamEvent.Token -> {
                            // Update streaming text
                            service.updateTextSelectionState(
                                    OmniAccessibilityService.textSelectionState.value.copy(
                                            optionsStreamingText = event.fullText
                                    )
                            )
                        }
                        is com.example.omni_link.ai.TextOptionStreamEvent.Complete -> {
                            // Options generated
                            Log.d(TAG, "Generated ${event.options.size} text options")
                            DebugLogManager.success(
                                    TAG,
                                    "AI Options Ready",
                                    "${event.options.size} options generated"
                            )
                            service.updateTextSelectionState(
                                    OmniAccessibilityService.textSelectionState.value.copy(
                                            isGeneratingOptions = false,
                                            textOptions = event.options,
                                            optionsStreamingText = ""
                                    )
                            )
                        }
                        is com.example.omni_link.ai.TextOptionStreamEvent.Error -> {
                            Log.e(TAG, "Text options generation error: ${event.message}")
                            DebugLogManager.error(TAG, "AI Options Failed", event.message)
                            service.updateTextSelectionState(
                                    OmniAccessibilityService.textSelectionState.value.copy(
                                            isGeneratingOptions = false,
                                            optionsStreamingText = "",
                                            error = event.message
                                    )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating text options", e)
                DebugLogManager.error(TAG, "AI Options Error", e.message ?: "Unknown error")
                service.updateTextSelectionState(
                        OmniAccessibilityService.textSelectionState.value.copy(
                                isGeneratingOptions = false,
                                optionsStreamingText = "",
                                error = e.message
                        )
                )
            }
        }
    }

    /** Execute an action from a text option */
    private fun executeTextOption(option: com.example.omni_link.ai.TextOption) {
        viewModelScope.launch {
            val service = OmniAccessibilityService.instance ?: return@launch

            Log.d(TAG, "Executing text option: ${option.title}")
            DebugLogManager.action(TAG, "Executing: ${option.title}", option.description)

            // Hide the text selection overlay first
            service.hideTextSelectionOverlay()

            // Small delay to let UI update
            delay(200)

            // Execute the action
            val result = service.executeAction(option.action)

            when (result) {
                is ActionResult.Success -> {
                    Log.d(TAG, "Text option executed: ${result.description}")
                    DebugLogManager.success(TAG, "Action Complete", result.description)
                }
                is ActionResult.Failure -> {
                    Log.e(TAG, "Text option failed: ${result.reason}")
                    DebugLogManager.error(TAG, "Action Failed", result.reason)
                }
                is ActionResult.NeedsConfirmation -> {
                    Log.w(TAG, "Text option needs confirmation: ${result.reason}")
                }
            }
        }
    }

    // Floating overlay state - directly observe from accessibility service for proper syncing
    // This ensures state is always in sync whether toggled from main app or accessibility shortcut
    val floatingOverlayEnabled: StateFlow<Boolean> = OmniAccessibilityService.floatingOverlayEnabled

    /** Enable floating overlay button */
    fun enableFloatingOverlay() {
        val service = OmniAccessibilityService.instance
        if (service != null) {
            service.showFloatingButton()
            Log.d(TAG, "Floating overlay enabled")
        } else {
            Log.w(TAG, "Cannot enable overlay - accessibility service not running")
        }
    }

    /** Disable floating overlay button */
    fun disableFloatingOverlay() {
        val service = OmniAccessibilityService.instance
        if (service != null) {
            service.hideFloatingButton()
            Log.d(TAG, "Floating overlay disabled")
        } else {
            Log.w(TAG, "Cannot disable overlay - accessibility service not running")
        }
    }

    /** Toggle floating overlay button */
    fun toggleFloatingOverlay() {
        val service = OmniAccessibilityService.instance
        if (service != null) {
            service.toggleFloatingButton()
            Log.d(TAG, "Floating overlay toggled: ${floatingOverlayEnabled.value}")
        } else {
            Log.w(TAG, "Cannot toggle overlay - accessibility service not running")
        }
    }

    /** Check if floating overlay is enabled */
    fun isFloatingOverlayEnabled(): Boolean {
        return floatingOverlayEnabled.value
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DEBUG OVERLAY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Enable debug overlay button */
    fun enableDebugOverlay() {
        OmniAccessibilityService.instance?.showDebugButton()
    }

    /** Disable debug overlay button */
    fun disableDebugOverlay() {
        OmniAccessibilityService.instance?.hideDebugButton()
    }

    /** Toggle debug overlay button */
    fun toggleDebugOverlay() {
        OmniAccessibilityService.instance?.toggleDebugButton()
    }

    /** Check if debug overlay is enabled */
    fun isDebugOverlayEnabled(): Boolean {
        return OmniAccessibilityService.instance?.isDebugOverlayEnabled() ?: false
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GLYPH METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Set the current glyph type and update the accessibility service */
    fun setGlyphType(type: GlyphType) {
        _currentGlyphType.value = type
        // Update the glyph helper in the accessibility service
        OmniAccessibilityService.instance?.setGlyphType(type)
        Log.d(TAG, "Glyph type changed to: ${type.displayName}")
    }

    override fun onCleared() {
        super.onCleared()
        // Clear callbacks to prevent memory leaks
        OmniAccessibilityService.onSuggestionButtonClicked = null
        OmniAccessibilityService.onSuggestionClicked = null
        OmniAccessibilityService.onDismissSuggestions = null
        // Clear focus area callbacks
        OmniAccessibilityService.onFocusAreaSelectionStart = null
        OmniAccessibilityService.onFocusAreaSelectionUpdate = null
        OmniAccessibilityService.onFocusAreaSelectionEnd = null
        OmniAccessibilityService.onFocusAreaClear = null
        OmniAccessibilityService.onFocusAreaConfirm = null
        // Clear text selection callbacks
        OmniAccessibilityService.onGenerateTextOptions = null
        OmniAccessibilityService.onTextOptionSelected = null
        // Clear fast forward callback
        OmniAccessibilityService.onFastForward = null
        viewModelScope.launch { llmProvider.unloadModel() }
    }
}

/** UI State for the assistant */
data class OmniUiState(
        val isLoading: Boolean = false,
        val isThinking: Boolean = false,
        val isModelReady: Boolean = false,
        val statusMessage: String = "Initializing...",
        val currentAction: String? = null,
        val currentModel: String? = null,
        val lastInferenceTimeMs: Long = 0,
        val totalTokensUsed: Int = 0
)
