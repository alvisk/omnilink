package com.example.omni_link.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni_link.ai.CactusLLMProvider
import com.example.omni_link.ai.ChatMessage
import com.example.omni_link.ai.LLMProvider
import com.example.omni_link.ai.MemoryItem
import com.example.omni_link.ai.ModelManager
import com.example.omni_link.data.AIAction
import com.example.omni_link.data.ActionResult
import com.example.omni_link.data.ScreenState
import com.example.omni_link.data.Suggestion
import com.example.omni_link.data.SuggestionState
import com.example.omni_link.data.db.MemoryRepository
import com.example.omni_link.data.db.OmniLinkDatabase
import com.example.omni_link.service.OmniAccessibilityService
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** ViewModel for the Omni-Link AI Assistant */
class OmniViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "OmniViewModel"
        private const val DEFAULT_MODEL = "qwen3-0.6" // Prefer an SDK slug by default
    }

    // Dependencies
    private val database = OmniLinkDatabase.getInstance(application)
    private val memoryRepository = MemoryRepository(database)
    val modelManager = ModelManager(application)
    // Share the CactusLM instance from ModelManager so provider knows about downloaded models
    private val llmProvider: LLMProvider = CactusLLMProvider(application, modelManager.cactusLM)

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
                                "👋 Hi! I'm Omni, your on-device AI assistant.\n\n" +
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
    // SUGGESTION OVERLAY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Generate suggestions based on current screen context */
    fun generateSuggestions() {
        viewModelScope.launch {
            if (!uiState.value.isModelReady) {
                _suggestionState.update {
                    it.copy(
                            isVisible = true,
                            isLoading = false,
                            error = "No AI model loaded. Please download a model first.",
                            suggestions = emptyList()
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
                            suggestions = emptyList()
                    )
                }
                return@launch
            }

            // Show loading state
            _suggestionState.update {
                it.copy(
                        isVisible = true,
                        isLoading = true,
                        error = null,
                        lastScreenContext = currentScreen.toPromptContext()
                )
            }

            try {
                val result = llmProvider.generateSuggestions(currentScreen, maxSuggestions = 5)

                if (result.isSuccess) {
                    val suggestions = result.getOrThrow()
                    Log.d(TAG, "Generated ${suggestions.size} suggestions")
                    _suggestionState.update {
                        it.copy(
                                isLoading = false,
                                suggestions = suggestions.sortedByDescending { s -> s.priority },
                                error = null
                        )
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.e(TAG, "Suggestion generation failed: $error")
                    _suggestionState.update {
                        it.copy(isLoading = false, error = error, suggestions = emptyList())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating suggestions", e)
                _suggestionState.update {
                    it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to generate suggestions",
                            suggestions = emptyList()
                    )
                }
            }
        }
    }

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

    /** Enable floating overlay button */
    fun enableFloatingOverlay() {
        OmniAccessibilityService.instance?.showFloatingButton()
    }

    /** Disable floating overlay button */
    fun disableFloatingOverlay() {
        OmniAccessibilityService.instance?.hideFloatingButton()
    }

    /** Toggle floating overlay button */
    fun toggleFloatingOverlay() {
        OmniAccessibilityService.instance?.toggleFloatingButton()
    }

    /** Check if floating overlay is enabled */
    fun isFloatingOverlayEnabled(): Boolean {
        return OmniAccessibilityService.instance?.isFloatingOverlayEnabled() ?: false
    }

    override fun onCleared() {
        super.onCleared()
        // Clear callbacks to prevent memory leaks
        OmniAccessibilityService.onSuggestionButtonClicked = null
        OmniAccessibilityService.onSuggestionClicked = null
        OmniAccessibilityService.onDismissSuggestions = null
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
