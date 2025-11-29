# Cactus SDK Integration Guide

This document explains the Cactus SDK integration in NOMM (Nothing On My Mind).

## Current Status

✅ **Cactus SDK is fully integrated!**

The app uses `com.cactuscompute:cactus:1.0.2-beta` from Maven Central.

## Architecture

### Files
- **`OmniApplication.kt`** - Initializes `CactusContextInitializer` on app startup
- **`CactusLLMProvider.kt`** - Main LLM inference using Cactus SDK
- **`ModelManager.kt`** - Downloads GGUF models from HuggingFace
- **`ModelSettingsScreen.kt`** - UI for downloading and managing AI models
- **`ChatScreen.kt`** - Main chat interface with model settings access

### Flow
1. App starts → `CactusContextInitializer.initialize(context)`
2. User downloads a model → `ModelManager.downloadModel(modelId)`
3. Model loads → `CactusLM().initializeModel(CactusInitParams(...))`
4. User sends message → `cactusLM.generateCompletion(messages, params)`
5. App shutdown → `cactusLM.unload()`

## Supported Models

The app's `ModelManager` supports these quantized GGUF models:

- **qwen2.5-0.5b-q4** (~400MB) - Ultra-fast, good for simple tasks
- **qwen2.5-1.5b-q4** (~1GB) - Balanced speed and quality
- **smollm2-135m-q8** (~150MB) - Tiny footprint, basic tasks
- **smollm2-360m-q8** (~400MB) - Small but capable

## Cactus SDK API (Kotlin)

```kotlin
import com.cactus.CactusContextInitializer
import com.cactus.CactusLM
import com.cactus.CactusInitParams
import com.cactus.CactusCompletionParams
import com.cactus.ChatMessage

// 1. Initialize context (required, do in Application.onCreate)
CactusContextInitializer.initialize(context)

// 2. Create LLM instance
val lm = CactusLM()

// 3. Download model (optional - use if model not present)
lm.downloadModel("qwen3-0.6") { progress, status, isError ->
    println("Download: $progress - $status")
}

// 4. Initialize model
lm.initializeModel(CactusInitParams(
    model = "qwen3-0.6",
    contextSize = 2048
))

// 5. Generate completion
val result = lm.generateCompletion(
    messages = listOf(
        ChatMessage(content = "You are a helpful assistant.", role = "system"),
        ChatMessage(content = "Hello!", role = "user")
    ),
    params = CactusCompletionParams(
        maxTokens = 512,
        temperature = 0.7
    )
)

println("Response: ${result?.response}")
println("Tokens/sec: ${result?.tokensPerSecond}")

// 6. Cleanup
lm.unload()
```

## Permissions Required

In `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" /> <!-- for STT -->
```

## Resources

- [Cactus Compute Website](https://cactuscompute.com/)
- [Kotlin SDK Docs](https://cactuscompute.com/docs/kotlin)
- [GitHub: cactus-compute/cactus](https://github.com/cactus-compute/cactus)
- [GitHub: cactus-compute/cactus-kotlin](https://github.com/cactus-compute/cactus-kotlin)
