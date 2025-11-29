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
import com.example.omni_link.data.FocusAreaSelectionState
import com.example.omni_link.data.FocusRegion
import com.example.omni_link.data.ScreenState
import com.example.omni_link.data.Suggestion
import com.example.omni_link.data.SuggestionState
import com.example.omni_link.data.db.MemoryRepository
import com.example.omni_link.data.db.OmniLinkDatabase
import com.example.omni_link.debug.DebugLogManager
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
            is AIAction.CaptureMedia ->
                    if (action.video) "Opening video camera..." else "Opening camera..."
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
    // SUGGESTION OVERLAY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Generate suggestions based on current screen context with streaming output */
    fun generateSuggestions() {
        viewModelScope.launch {
            if (!uiState.value.isModelReady) {
                _suggestionState.update {
                    it.copy(
                            isVisible = true,
                            isLoading = false,
                            error = "No AI model loaded. Please download a model first.",
                            suggestions = emptyList(),
                            streamingText = "",
                            isStreaming = false
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
                            isStreaming = false
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
            _suggestionState.update {
                it.copy(
                        isVisible = true,
                        isLoading = true,
                        isStreaming = true,
                        streamingText = "",
                        error = null,
                        lastScreenContext = screenContext
                )
            }

            try {
                // Use streaming generation with focus region awareness
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
                                    Log.d(TAG, "Generated ${event.suggestions.size} suggestions")
                                    _suggestionState.update {
                                        it.copy(
                                                isLoading = false,
                                                isStreaming = false,
                                                suggestions =
                                                        event.suggestions.sortedByDescending { s ->
                                                            s.priority
                                                        },
                                                error = null,
                                                streamingText = ""
                                        )
                                    }
                                }
                                is com.example.omni_link.ai.SuggestionStreamEvent.Error -> {
                                    Log.e(TAG, "Suggestion generation failed: ${event.message}")
                                    _suggestionState.update {
                                        it.copy(
                                                isLoading = false,
                                                isStreaming = false,
                                                error = event.message,
                                                suggestions = emptyList(),
                                                streamingText = ""
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
                            error = e.message ?: "Failed to generate suggestions",
                            suggestions = emptyList(),
                            streamingText = ""
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
