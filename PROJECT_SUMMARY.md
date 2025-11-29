# NOMM: Nothing On My Mind - On-Device AI Assistant for Android

## Project Overview

**NOMM (Nothing On My Mind)** is a privacy-focused Android AI assistant that runs entirely on-device using the Cactus SDK for local LLM inference. It uses Android's Accessibility Service to see and interact with any app on the phone, enabling voice/text commands to control the device.

### Core Value Proposition
- **100% Private**: All AI processing happens locally on the device
- **Universal Control**: Can see and interact with any app via Accessibility Service
- **Memory System**: Persistent memory across sessions using Room database
- **Modern UI**: Blocky black & red aesthetic inspired by Nothing Phone design

---

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin 2.2.0 |
| UI Framework | Jetpack Compose (BOM 2024.02.00) |
| AI/LLM | Cactus SDK 1.0.2-beta (on-device inference) |
| Database | Room 2.8.4 |
| Architecture | MVVM with ViewModel + StateFlow |
| Build System | Gradle 8.6.0 with KSP |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |

---

## Project Structure

```
com.example.omni_link/
├── MainActivity.kt           # Entry point, hosts Compose UI
├── OmniApplication.kt        # App class, initializes Cactus SDK
├── ai/
│   ├── LLMProvider.kt        # Interface for LLM inference abstraction
│   ├── CactusLLMProvider.kt  # Cactus SDK implementation (~790 lines)
│   └── ModelManager.kt       # Model download/management (~580 lines)
├── data/
│   ├── AIAction.kt           # Sealed classes for device actions
│   ├── ScreenElement.kt      # Screen state data models
│   └── db/
│       └── MemoryDatabase.kt # Room DB for persistent memory
├── service/
│   └── OmniAccessibilityService.kt  # Screen capture & action execution
└── ui/
    ├── ChatScreen.kt         # Main chat interface (~787 lines)
    ├── ModelSettingsScreen.kt # Model download/selection UI
    ├── OmniViewModel.kt      # Main ViewModel (~510 lines)
    └── theme/
        ├── Theme.kt          # Black/red color scheme
        └── Type.kt           # Nothing Phone typography
```

---

## Key Components

### 1. AI Provider System (`ai/`)

#### LLMProvider Interface
```kotlin
interface LLMProvider {
    suspend fun isReady(): Boolean
    suspend fun loadModel(modelPath: String): Result<Unit>
    suspend fun unloadModel()
    suspend fun generateResponse(
        userMessage: String,
        screenState: ScreenState?,
        conversationHistory: List<ChatMessage>,
        memory: List<MemoryItem>
    ): Result<LLMResponse>
    fun parseActions(response: String): ActionPlan
}
```

#### CactusLLMProvider
- Implements LLMProvider using Cactus SDK
- Supports loading models by slug (e.g., "qwen3-0.6") or GGUF file path
- Has a comprehensive system prompt that instructs the AI to respond in JSON format with:
  - `thought`: Brief reasoning
  - `response`: User-facing message
  - `actions`: Array of device actions (click, type, scroll, etc.)
  - `memory`: Key-value pairs to persist
- Includes fallback "smart response" system for pattern matching when LLM unavailable
- Parses JSON responses to extract ActionPlan objects

#### ModelManager
- Manages Cactus SDK model downloads
- Supports two model types:
  1. **Cactus SDK models** (preferred): Downloaded via `cactusLM.downloadModel(slug)`
  2. **Legacy GGUF files**: Manual download with progress tracking
- Default models: `qwen3-0.6` (400MB), `gemma3-270m` (270MB)
- Tracks download progress and model availability
- Handles network connectivity checks

### 2. Accessibility Service (`service/OmniAccessibilityService.kt`)

**Purpose**: Captures screen content and executes AI-directed actions.

**Key Capabilities**:
- Screen capture via `rootInActiveWindow`
- Element extraction (text, content descriptions, bounds, interactivity)
- Action execution:
  - **Click**: By label, index, or coordinates
  - **Type**: Into text fields with optional clear
  - **Scroll**: Using gestures (up/down/left/right)
  - **Navigate**: Back, Home, Open app by name
  - **Wait**: Timed delays

**Screen State Model**:
```kotlin
data class ScreenState(
    val packageName: String,
    val activityName: String?,
    val timestamp: Long,
    val elements: List<ScreenElement>
)

data class ScreenElement(
    val id: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    // ... children for nested elements
)
```

**App Opening**: Maps common app names to package names (Settings, Chrome, Messages, Phone, Clock, Photos, etc.)

### 3. AI Action System (`data/AIAction.kt`)

```kotlin
sealed class AIAction {
    data class Click(val target: String, val index: Int? = null)
    data class Type(val target: String, val text: String, val clearFirst: Boolean = true)
    data class Scroll(val direction: ScrollDirection, val target: String? = null)
    object Back
    object Home
    data class OpenApp(val appName: String)
    data class Wait(val milliseconds: Long = 1000)
    data class Respond(val message: String)
    data class Clarify(val question: String)
    data class Complete(val summary: String)
}

data class ActionPlan(
    val reasoning: String,
    val actions: List<AIAction>,
    val isComplete: Boolean = false
)
```

### 4. Memory System (`data/db/MemoryDatabase.kt`)

**Entities**:
1. **MemoryEntity**: Persistent knowledge storage
   - Key-value pairs with categories (preference, fact, context, task)
   - Importance scoring (1-10)
   - Access counting for relevance
   - Optional expiration

2. **MessageEntity**: Chat history
   - Role (USER, ASSISTANT, SYSTEM)
   - Content and timestamp
   - Session grouping

**Repository Pattern**: `MemoryRepository` provides methods like `remember()`, `recall()`, `getContextMemories()`, `search()`, `forget()`

### 5. UI System (`ui/`)

#### Theme (Black & Red Blocky Design)
```kotlin
// Primary Colors (NOMM = Nothing On My Mind)
val NothingRed = Color(0xFFD71921)   // Nothing's signature red
val NOMMBlack = Color(0xFF000000)    // Pure black background
val NOMMGrayDark = Color(0xFF141414) // Card backgrounds
val NothingRedGlow = Color(0xFFFF2D36) // Brighter red for emphasis

// Typography: Nothing Phone fonts
// - NDot55: Dot-matrix display font for headlines
// - NDot57: Variant for titles
// - NType82: Clean geometric sans for body text
```

#### ChatScreen Components
- `BlockyTopBar`: Status indicator (ON/OFF), model name, inference time display
- `BlockyChatBubble`: User/AI message bubbles with blocky borders
- `BlockyThinkingIndicator`: Animated pulsing dots during inference
- `BlockyInputBar`: Text input with send button
- `BlockyEmptyState`: Grid pattern logo with command suggestions
- `BlockyPermissionSheet`: Accessibility/Overlay permission setup
- `BlockyWarningBanner`: Service offline notification

#### ModelSettingsScreen Components
- Model status card (ON/OFF state)
- Storage info (model count, total size)
- Model cards with download progress
- Delete confirmation dialog

### 6. ViewModel (`ui/OmniViewModel.kt`)

**State Management**:
```kotlin
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
```

**Key Functions**:
- `initializeModel()`: Auto-loads first downloaded model
- `sendMessage(text)`: Main chat flow - captures screen, generates response, executes actions
- `downloadCactusModel(slug)`: Initiates model download
- `selectCactusModel(slug)`: Switches to downloaded model
- `executeActionPlan(actions)`: Runs AI-generated action sequence

---

## System Prompt

The AI is instructed to respond in JSON format:
```json
{
  "thought": "Brief reasoning about what you're doing",
  "response": "Natural language response to show the user",
  "actions": [
    {"type": "click", "target": "button text"},
    {"type": "type", "target": "field", "text": "text to enter"},
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

---

## Required Permissions

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

**Runtime Permissions**:
- Accessibility Service (manual toggle in Settings)
- Overlay permission (for floating UI)

---

## Dependencies

```kotlin
// Core
implementation("androidx.core:core-ktx:1.15.0")
implementation("androidx.appcompat:appcompat:1.6.1")

// Compose (BOM 2024.02.00)
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-extended")
implementation("androidx.activity:activity-compose:1.8.2")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
implementation("androidx.navigation:navigation-compose:2.7.7")

// Cactus SDK (On-device LLM)
implementation("com.cactuscompute:cactus:1.0.2-beta")

// Room Database
implementation("androidx.room:room-runtime:2.8.4")
implementation("androidx.room:room-ktx:2.8.4")
ksp("androidx.room:room-compiler:2.8.4")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// JSON
implementation("com.google.code.gson:gson:2.10.1")
```

---

## Configuration Files

### accessibility_config.xml
```xml
<accessibility-service
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:accessibilityFlags="flagDefault|flagReportViewIds|flagIncludeNotImportantViews" />
```

### build.gradle.kts (app)
- `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`
- `android:largeHeap="true"` for LLM memory
- Java 17 compatibility

---

## User Flow

1. **First Launch**:
   - App checks for downloaded models
   - Shows "No model downloaded" message
   - User goes to Model Settings, downloads a model (qwen3-0.6 recommended)

2. **Setup**:
   - User enables Accessibility Service in Settings
   - Optional: Grant overlay permission for floating button

3. **Usage**:
   - User types command in chat (e.g., "open settings", "scroll down", "tap on WiFi")
   - AI receives: user message + current screen state + conversation history + memory
   - AI responds with JSON containing actions to execute
   - Service executes actions on device
   - Response displayed in chat

4. **Example Commands**:
   - "What's on my screen?" - AI describes visible elements
   - "Open Calculator" - Launches Calculator app
   - "Scroll down" - Performs scroll gesture
   - "Tap on Settings" - Clicks the Settings button
   - "Type 'hello world'" - Types into focused field

---

## Design System

### Colors
| Name | Hex | Usage |
|------|-----|-------|
| NOMMRed | #D71921 | Primary accent, active states |
| NOMMBlack | #000000 | Background |
| NOMMGrayDark | #141414 | Card backgrounds |
| NOMMGrayMid | #262626 | Borders |
| NOMMGrayText | #808080 | Secondary text |
| NOMMWhite | #FFFFFF | Primary text |
| NothingRed | #D71921 | Success states |

### Typography
- **Headlines**: NDot55 (dot-matrix aesthetic)
- **Titles**: NDot57
- **Body**: NType82 (geometric sans-serif)
- All text uses UPPERCASE with letter-spacing for blocky feel

### UI Elements
- Sharp corners (RoundedCornerShape(0.dp))
- 2dp borders on cards
- Blocky status indicators
- Pulsing animations for loading states

---

## Custom Fonts Required

Place these in `res/font/`:
- `ndot55_regular.otf`
- `ndot57_regular.otf`
- `ntype82_regular.otf`
- `ntype82_headline.otf`

These are Nothing Phone brand fonts that give the UI its distinctive look.

---

## Key Implementation Notes

1. **Cactus SDK Initialization**: Must call `CactusContextInitializer.initialize(context)` in Application.onCreate()

2. **Model Loading**: Support both slug-based (SDK catalog) and path-based (legacy GGUF) loading

3. **Screen Capture Debouncing**: Use 300ms debounce on accessibility events to avoid overwhelming the system

4. **Action Parsing**: Handle both valid JSON responses and plain text fallbacks

5. **Memory Context**: Include top 10-20 memories in LLM context for continuity

6. **Gesture Clicks**: Use GestureDescription for clicking when AccessibilityNodeInfo.performAction fails

7. **App Opening**: Maintain mapping of common app names to package names for reliability

---

## Future Enhancements (Suggested)

1. **Track 1 - Memory Master**: More sophisticated memory categorization and retrieval
2. **Track 2 - Action Hero**: Complex multi-step action sequences
3. **Floating Overlay**: Always-accessible quick action button
4. **Voice Input**: Speech-to-text integration
5. **Learning Mode**: Record and replay user workflows

---

## Build & Run

```bash
# Clone and open in Android Studio
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk

# Enable Accessibility Service
# Settings > Accessibility > NOMM > Enable
```

**Testing Requirements**:
- Physical device or emulator with API 26+
- Network connectivity for model download
- Sufficient storage (~400MB for default model)

---

*Generated from codebase analysis on Nov 28, 2025*


