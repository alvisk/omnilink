package com.example.omni_link.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.cactus.CactusLM
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive

/**
 * Manages model downloads and storage for Cactus SDK
 *
 * Supports two sources: 1) Cactus SDK catalog (preferred) — use SDK-managed downloads and slugs 2)
 * Legacy custom GGUF downloads — kept for compatibility
 */
class ModelManager(private val context: Context) {

        companion object {
                private const val TAG = "ModelManager"
                private const val MODELS_DIR = "models"

                /** Cactus SDK models - Local server models (local- prefix) and cloud models */
                val CACTUS_EXAMPLE_MODELS =
                        listOf(
                                // === LOCAL SERVER MODELS (download from local server) ===
                                CactusModelInfo(
                                        slug = "local-qwen3-0.6",
                                        name = "Qwen3 0.6B (Local)",
                                        sizeMb = 417,
                                        description = "Fast & capable, from local server",
                                        isLocal = true
                                ),
                                CactusModelInfo(
                                        slug = "local-qwen3-1.7",
                                        name = "Qwen3 1.7B (Local)",
                                        sizeMb = 1200,
                                        description = "Larger Qwen3, better quality",
                                        isLocal = true
                                ),
                                CactusModelInfo(
                                        slug = "local-gemma3-270m",
                                        name = "Gemma3 270M (Local)",
                                        sizeMb = 172,
                                        description = "Compact Google Gemma model",
                                        isLocal = true
                                ),
                                CactusModelInfo(
                                        slug = "local-gemma3-1b",
                                        name = "Gemma3 1B (Local)",
                                        sizeMb = 642,
                                        description = "Larger Gemma model",
                                        isLocal = true
                                ),
                                CactusModelInfo(
                                        slug = "local-smollm2-360m",
                                        name = "SmolLM2 360M (Local)",
                                        sizeMb = 238,
                                        description = "Ultra-compact, fast inference",
                                        isLocal = true
                                ),
                                CactusModelInfo(
                                        slug = "local-lfm2-350m",
                                        name = "LFM2 350M (Local)",
                                        sizeMb = 233,
                                        description = "Liquid Foundation Model, compact",
                                        isLocal = true
                                ),
                                CactusModelInfo(
                                        slug = "local-lfm2-700m",
                                        name = "LFM2 700M (Local)",
                                        sizeMb = 467,
                                        description = "Liquid Foundation Model, balanced",
                                        isLocal = true
                                ),
                                CactusModelInfo(
                                        slug = "local-lfm2-1.2b",
                                        name = "LFM2 1.2B (Local)",
                                        sizeMb = 722,
                                        description = "Liquid Foundation Model, powerful",
                                        isLocal = true
                                ),
                                CactusModelInfo(
                                        slug = "local-lfm2-vl-450m",
                                        name = "LFM2 Vision 450M (Local)",
                                        sizeMb = 421,
                                        description = "Vision model, can analyze images",
                                        isLocal = true
                                ),
                                CactusModelInfo(
                                        slug = "local-lfm2-vl-1.6b",
                                        name = "LFM2 Vision 1.6B (Local)",
                                        sizeMb = 1400,
                                        description = "Large vision model",
                                        isLocal = true
                                ),
                                // === CLOUD MODELS (download from Cactus servers) ===
                                CactusModelInfo(
                                        slug = "lfm2-350m",
                                        name = "LFM2 350M",
                                        sizeMb = 233,
                                        description = "Recommended - Liquid AI, fast & efficient"
                                ),
                                CactusModelInfo(
                                        slug = "qwen3-0.6",
                                        name = "Qwen3 0.6B",
                                        sizeMb = 417,
                                        description = "Versatile model, good quality"
                                ),
                                CactusModelInfo(
                                        slug = "gemma3-270m",
                                        name = "Gemma3 270M",
                                        sizeMb = 172,
                                        description = "Compact Google Gemma model"
                                )
                        )

                /** Legacy, custom GGUF models (still available for manual downloads) */
                val AVAILABLE_MODELS = emptyMap<String, ModelInfo>() // Disabled legacy models
        }

        data class ModelInfo(
                val name: String,
                val url: String,
                val sizeBytes: Long,
                val description: String
        )

        /** Info for Cactus SDK example models */
        data class CactusModelInfo(
                val slug: String,
                val name: String,
                val sizeMb: Int,
                val description: String,
                val isDownloaded: Boolean = false,
                val isLocal: Boolean =
                        false // Local models use "local-" prefix and don't need download
        )

        sealed class DownloadState {
                object Idle : DownloadState()
                /** Determinate progress for legacy manual downloads */
                data class Downloading(
                        val progress: Float,
                        val bytesDownloaded: Long,
                        val totalBytes: Long
                ) : DownloadState()
                /** Legacy completion with a real file */
                data class Completed(val file: File) : DownloadState()
                /** Completion for Cactus SDK downloads (identified by slug) */
                data class CompletedCactus(val slug: String) : DownloadState()
                data class Failed(val error: String) : DownloadState()
        }

        /** Per-model download state for simultaneous downloads */
        data class ModelDownloadState(
                val slug: String,
                val progress: Float = 0f,
                val bytesDownloaded: Long = 0L,
                val totalBytes: Long = 0L,
                val isDownloading: Boolean = false,
                val isCompleted: Boolean = false,
                val error: String? = null
        )

        // Track multiple simultaneous downloads
        private val _activeDownloads = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
        val activeDownloads: StateFlow<Map<String, ModelDownloadState>> = _activeDownloads

        /** Get download state for a specific model */
        fun getModelDownloadState(slug: String): ModelDownloadState? = _activeDownloads.value[slug]

        /** Check if a model is currently downloading */
        fun isModelDownloading(slug: String): Boolean =
                _activeDownloads.value[slug]?.isDownloading == true

        /** Update download state for a model */
        private fun updateDownloadState(
                slug: String,
                update: (ModelDownloadState?) -> ModelDownloadState?
        ) {
                _activeDownloads.update { current ->
                        val newState = update(current[slug])
                        if (newState != null) {
                                current + (slug to newState)
                        } else {
                                current - slug
                        }
                }
        }

        /** Clear completed/failed download state for a model */
        fun clearDownloadState(slug: String) {
                _activeDownloads.update { it - slug }
        }

        private val modelsDir: File by lazy {
                File(context.filesDir, MODELS_DIR).also { it.mkdirs() }
        }

        /** Check if device has internet connectivity */
        fun isInternetAvailable(): Boolean {
                val connectivityManager =
                        context.getSystemService(Context.CONNECTIVITY_SERVICE) as
                                ConnectivityManager
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities =
                        connectivityManager.getNetworkCapabilities(network) ?: return false
                return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        // --- Cactus SDK ---
        // Exposed so CactusLLMProvider can share this instance (has download state)
        val cactusLM: CactusLM by lazy { CactusLM() }

        // --- Cactus SDK Example Models State ---
        private val _cactusModels = MutableStateFlow<List<CactusModelInfo>>(CACTUS_EXAMPLE_MODELS)
        val cactusModels: StateFlow<List<CactusModelInfo>> = _cactusModels

        /** Refresh download status for example models by querying SDK catalog */
        suspend fun refreshCactusModels() {
                try {
                        // Query SDK catalog to check which models are downloaded
                        val sdkModels = cactusLM.getModels()
                        _cactusModels.update { current ->
                                current.map { model ->
                                        // Find matching SDK model and check isDownloaded
                                        val sdkModel =
                                                sdkModels.firstOrNull { it.slug == model.slug }
                                        model.copy(isDownloaded = sdkModel?.isDownloaded == true)
                                }
                        }
                        Log.d(TAG, "Refreshed Cactus models: ${sdkModels.size} available")
                } catch (e: Exception) {
                        Log.e(TAG, "Failed to refresh Cactus model status", e)
                }
        }

        fun isCactusModelDownloaded(slug: String): Boolean =
                _cactusModels.value.firstOrNull { it.slug == slug }?.isDownloaded == true

        fun getCactusDownloadedCount(): Int = _cactusModels.value.count { it.isDownloaded }

        fun getCactusDownloadedSizeBytes(): Long =
                _cactusModels.value.filter { it.isDownloaded }.sumOf {
                        it.sizeMb.toLong() * 1_000_000L
                }

        /**
         * Download a Cactus SDK model by slug using the official SDK. See:
         * https://cactuscompute.com/docs/kotlin
         *
         * Progress is tracked by monitoring file size in potential download locations. Models with
         * "local-" prefix are downloaded the same way - the prefix tells Cactus to use locally
         * hosted inference after download.
         */
    fun downloadCactusModel(slug: String): Flow<DownloadState> =
            flow {
                    // Check if model is already downloaded - skip if present
                    if (isCactusModelDownloaded(slug)) {
                        Log.d(TAG, "Model $slug is already downloaded, skipping download")
                        emit(DownloadState.CompletedCactus(slug))
                        return@flow
                    }

                    // Check internet connectivity first
                    if (!isInternetAvailable()) {
                                        Log.e(TAG, "No internet connection available")
                                        updateDownloadState(slug) {
                                                ModelDownloadState(
                                                        slug = slug,
                                                        error = "No internet connection"
                                                )
                                        }
                                        emit(
                                                DownloadState.Failed(
                                                        "No internet connection. Please check your network and try again."
                                                )
                                        )
                                        return@flow
                                }

                                // Get model info
                                val modelInfo = _cactusModels.value.firstOrNull { it.slug == slug }
                                val expectedBytes = (modelInfo?.sizeMb ?: 400) * 1_000_000L

                                // Initialize per-model download state
                                updateDownloadState(slug) {
                                        ModelDownloadState(
                                                slug = slug,
                                                progress = 0f,
                                                bytesDownloaded = 0,
                                                totalBytes = expectedBytes,
                                                isDownloading = true
                                        )
                                }

                                // Emit initial downloading state immediately to avoid UI flicker
                                emit(
                                        DownloadState.Downloading(
                                                progress = 0f,
                                                bytesDownloaded = 0,
                                                totalBytes = expectedBytes
                                        )
                                )

                                try {

                                        Log.d(TAG, "========== DOWNLOAD START ==========")
                                        Log.d(TAG, "Model slug: $slug")
                                        Log.d(
                                                TAG,
                                                "Expected size: ${expectedBytes / 1_000_000}MB ($expectedBytes bytes)"
                                        )
                                        Log.d(TAG, "filesDir: ${context.filesDir.absolutePath}")
                                        Log.d(TAG, "cacheDir: ${context.cacheDir.absolutePath}")
                                        Log.d(
                                                TAG,
                                                "externalFilesDir: ${context.getExternalFilesDir(null)?.absolutePath}"
                                        )

                                        // Log initial directory contents
                                        logDirectoryContents("filesDir BEFORE", context.filesDir)
                                        logDirectoryContents("cacheDir BEFORE", context.cacheDir)

                                        coroutineScope {
                                                // Track if download is complete
                                                var downloadComplete = false
                                                var downloadError: Exception? = null

                                                // Start the SDK download in background
                                                Log.d(
                                                        TAG,
                                                        "Starting cactusLM.downloadModel($slug)..."
                                                )
                                                val downloadJob =
                                                        async(Dispatchers.IO) {
                                                                try {
                                                                        Log.d(
                                                                                TAG,
                                                                                "SDK downloadModel() called"
                                                                        )
                                                                        cactusLM.downloadModel(slug)
                                                                        Log.d(
                                                                                TAG,
                                                                                "SDK downloadModel() returned successfully"
                                                                        )
                                                                        downloadComplete = true
                                                                } catch (e: Exception) {
                                                                        Log.e(
                                                                                TAG,
                                                                                "SDK downloadModel() threw exception: ${e.message}",
                                                                                e
                                                                        )
                                                                        downloadError = e
                                                                        throw e
                                                                }
                                                        }

                                                // Monitor progress by checking file sizes
                                                var loopCount = 0
                                                var lastBytesDownloaded = 0L

                                                while (!downloadComplete &&
                                                        downloadError == null &&
                                                        isActive) {
                                                        delay(1000) // Check every 1 second
                                                        loopCount++

                                                        // Scan for any new/growing files
                                                        val scanResult =
                                                                scanForDownloadingFiles(slug)

                                                        Log.d(
                                                                TAG,
                                                                "--- Monitor loop #$loopCount ---"
                                                        )
                                                        Log.d(
                                                                TAG,
                                                                "downloadComplete=$downloadComplete, downloadError=$downloadError"
                                                        )
                                                        Log.d(
                                                                TAG,
                                                                "Scan result: ${scanResult.first?.absolutePath ?: "NO FILE FOUND"}, size=${scanResult.second}"
                                                        )

                                                        // Every 5 loops, log full directory
                                                        // contents
                                                        if (loopCount % 5 == 0) {
                                                                logDirectoryContents(
                                                                        "filesDir DURING",
                                                                        context.filesDir
                                                                )
                                                                logDirectoryContents(
                                                                        "cacheDir DURING",
                                                                        context.cacheDir
                                                                )
                                                        }

                                                        val currentBytes = scanResult.second
                                                        if (currentBytes > 0) {
                                                                val progress =
                                                                        (currentBytes.toFloat() /
                                                                                        expectedBytes)
                                                                                .coerceIn(0f, 0.99f)
                                                                Log.d(
                                                                        TAG,
                                                                        "PROGRESS: ${(progress * 100).toInt()}% - $currentBytes / $expectedBytes bytes"
                                                                )
                                                                Log.d(
                                                                        TAG,
                                                                        "File: ${scanResult.first?.absolutePath}"
                                                                )

                                                                // Update per-model state
                                                                updateDownloadState(slug) {
                                                                        ModelDownloadState(
                                                                                slug = slug,
                                                                                progress = progress,
                                                                                bytesDownloaded =
                                                                                        currentBytes,
                                                                                totalBytes =
                                                                                        expectedBytes,
                                                                                isDownloading = true
                                                                        )
                                                                }

                                                                emit(
                                                                        DownloadState.Downloading(
                                                                                progress = progress,
                                                                                bytesDownloaded =
                                                                                        currentBytes,
                                                                                totalBytes =
                                                                                        expectedBytes
                                                                        )
                                                                )
                                                                lastBytesDownloaded = currentBytes
                                                        } else {
                                                                Log.d(
                                                                        TAG,
                                                                        "No download file found yet (loop #$loopCount)"
                                                                )
                                                        }

                                                        // Check if download job completed
                                                        if (downloadJob.isCompleted) {
                                                                Log.d(TAG, "Download job completed")
                                                                break
                                                        }
                                                }

                                                // Wait for download to finish
                                                Log.d(TAG, "Awaiting download job...")
                                                downloadJob.await()
                                                Log.d(TAG, "Download job await completed")
                                        }

                                        // Log directory contents after download
                                        Log.d(TAG, "========== DOWNLOAD COMPLETE ==========")
                                        logDirectoryContents("filesDir AFTER", context.filesDir)
                                        logDirectoryContents("cacheDir AFTER", context.cacheDir)

                                        // Final progress - mark as complete
                                        updateDownloadState(slug) {
                                                ModelDownloadState(
                                                        slug = slug,
                                                        progress = 1f,
                                                        bytesDownloaded = expectedBytes,
                                                        totalBytes = expectedBytes,
                                                        isDownloading = false,
                                                        isCompleted = true
                                                )
                                        }

                                        emit(
                                                DownloadState.Downloading(
                                                        progress = 1f,
                                                        bytesDownloaded = expectedBytes,
                                                        totalBytes = expectedBytes
                                                )
                                        )

                                        // Refresh catalog so isDownloaded updates
                                        refreshCactusModels()
                                        Log.d(TAG, "Successfully downloaded model: $slug")
                                        emit(DownloadState.CompletedCactus(slug))
                                } catch (e: Exception) {
                                        Log.e(TAG, "========== DOWNLOAD FAILED ==========")
                                        Log.e(TAG, "Error: ${e.message}", e)
                                        logDirectoryContents("filesDir ON ERROR", context.filesDir)
                                        logDirectoryContents("cacheDir ON ERROR", context.cacheDir)

                                        // Update per-model state with error
                                        updateDownloadState(slug) {
                                                ModelDownloadState(
                                                        slug = slug,
                                                        isDownloading = false,
                                                        error = e.message ?: "Download error"
                                                )
                                        }

                                        emit(DownloadState.Failed(e.message ?: "Download error"))
                                }
                        }
                        .flowOn(Dispatchers.IO)

        /** Log all files in a directory recursively */
        private fun logDirectoryContents(label: String, dir: File) {
                Log.d(TAG, "--- $label: ${dir.absolutePath} ---")
                try {
                        dir.walkTopDown().maxDepth(4).forEach { file ->
                                val size = if (file.isFile) " (${file.length()} bytes)" else ""
                                val indent =
                                        "  ".repeat(
                                                file.absolutePath.removePrefix(dir.absolutePath)
                                                        .count { it == '/' }
                                        )
                                Log.d(TAG, "$indent${file.name}$size")
                        }
                } catch (e: Exception) {
                        Log.e(TAG, "Error listing directory: ${e.message}")
                }
        }

        /** Scan all app directories for any file that might be the download */
        private fun scanForDownloadingFiles(slug: String): Pair<File?, Long> {
                var bestFile: File? = null
                var bestSize = 0L

                // Directories to scan
                val dirsToScan =
                        listOfNotNull(
                                context.filesDir,
                                context.cacheDir,
                                context.getExternalFilesDir(null),
                                context.codeCacheDir,
                                context.noBackupFilesDir
                        )

                for (dir in dirsToScan) {
                        try {
                                dir.walkTopDown().maxDepth(5).forEach { file ->
                                        if (file.isFile && file.length() > bestSize) {
                                                val name = file.name.lowercase()
                                                // Check if file might be related to our download
                                                if (name.contains(slug.lowercase()) ||
                                                                name.contains("gguf") ||
                                                                name.contains("model") ||
                                                                name.contains("download") ||
                                                                name.endsWith(".tmp") ||
                                                                name.endsWith(".part") ||
                                                                // Also check for large files that
                                                                // appeared recently
                                                                (file.length() > 10_000_000 &&
                                                                        file.lastModified() >
                                                                                System.currentTimeMillis() -
                                                                                        60_000)
                                                ) {
                                                        bestFile = file
                                                        bestSize = file.length()
                                                        Log.d(
                                                                TAG,
                                                                "Found candidate file: ${file.absolutePath} (${file.length()} bytes)"
                                                        )
                                                }
                                        }
                                }
                        } catch (e: Exception) {
                                Log.e(TAG, "Error scanning ${dir.absolutePath}: ${e.message}")
                        }
                }

                return Pair(bestFile, bestSize)
        }

        // --- Legacy GGUF download support (kept for compatibility) ---

        /** Get list of downloaded legacy models (GGUF files) */
        fun getDownloadedModels(): List<File> {
                return modelsDir.listFiles { file -> file.extension == "gguf" }?.toList()
                        ?: emptyList()
        }

        /** Check if a legacy model is downloaded */
        fun isModelDownloaded(modelId: String): Boolean {
                val modelFile = File(modelsDir, "$modelId.gguf")
                return modelFile.exists() && modelFile.length() > 0
        }

        /** Get the path to a downloaded legacy model */
        fun getModelPath(modelId: String): String? {
                val modelFile = File(modelsDir, "$modelId.gguf")
                return if (modelFile.exists()) modelFile.absolutePath else null
        }

        /** Legacy manual download with progress updates (determinate) */
        fun downloadModel(modelId: String): Flow<DownloadState> =
                flow {
                                // Check internet connectivity first
                                if (!isInternetAvailable()) {
                                        Log.e(TAG, "No internet connection available")
                                        emit(
                                                DownloadState.Failed(
                                                        "No internet connection. Please check your network and try again."
                                                )
                                        )
                                        return@flow
                                }

                                val modelInfo = AVAILABLE_MODELS[modelId]
                                if (modelInfo == null) {
                                        emit(DownloadState.Failed("Unknown model: $modelId"))
                                        return@flow
                                }

                                // Emit initial downloading state immediately to avoid UI flicker
                                emit(
                                        DownloadState.Downloading(
                                                progress = 0f,
                                                bytesDownloaded = 0,
                                                totalBytes = modelInfo.sizeBytes
                                        )
                                )

                                val outputFile = File(modelsDir, "$modelId.gguf")
                                val tempFile = File(modelsDir, "$modelId.gguf.tmp")

                                try {
                                        Log.d(
                                                TAG,
                                                "Starting download: ${modelInfo.name} from ${modelInfo.url}"
                                        )

                                        // Setup SSL context that trusts all certificates (for
                                        // emulator
                                        // compatibility)
                                        setupTrustAllCerts()

                                        var connection: HttpURLConnection? = null
                                        var currentUrl = modelInfo.url
                                        var redirectCount = 0
                                        val maxRedirects = 5

                                        // Handle redirects manually (HuggingFace uses redirects)
                                        while (redirectCount < maxRedirects) {
                                                Log.d(
                                                        TAG,
                                                        "Connecting to: $currentUrl (attempt ${redirectCount + 1})"
                                                )
                                                val url = URL(currentUrl)
                                                connection =
                                                        url.openConnection() as HttpURLConnection
                                                connection.connectTimeout =
                                                        30_000 // 30 seconds connect
                                                connection.readTimeout = 120_000 // 2 minutes read
                                                connection.setRequestProperty(
                                                        "User-Agent",
                                                        "Mozilla/5.0 (Android) NOMM/1.0"
                                                )
                                                connection.setRequestProperty("Accept", "*/*")
                                                connection.instanceFollowRedirects = false

                                                Log.d(TAG, "Waiting for response...")
                                                val responseCode = connection.responseCode
                                                Log.d(
                                                        TAG,
                                                        "Response code: $responseCode for $currentUrl"
                                                )

                                                if (responseCode in 300..399) {
                                                        val newUrl =
                                                                connection.getHeaderField(
                                                                        "Location"
                                                                )
                                                        if (newUrl != null) {
                                                                Log.d(
                                                                        TAG,
                                                                        "Redirecting to: $newUrl"
                                                                )
                                                                currentUrl = newUrl
                                                                connection.disconnect()
                                                                redirectCount++
                                                                continue
                                                        }
                                                }
                                                break
                                        }

                                        if (connection == null || connection.responseCode != 200) {
                                                val errorCode = connection?.responseCode ?: -1
                                                throw Exception("HTTP error: $errorCode")
                                        }

                                        val totalBytes =
                                                connection.contentLengthLong.takeIf { it > 0 }
                                                        ?: modelInfo.sizeBytes
                                        var bytesDownloaded = 0L
                                        var lastEmitTime = System.currentTimeMillis()

                                        Log.d(TAG, "Download size: ${totalBytes / 1024 / 1024}MB")

                                        connection.inputStream.use { input ->
                                                FileOutputStream(tempFile).use { output ->
                                                        val buffer =
                                                                ByteArray(
                                                                        32768
                                                                ) // 32KB buffer for better
                                                        // performance
                                                        var bytesRead: Int

                                                        while (input.read(buffer).also {
                                                                bytesRead = it
                                                        } != -1) {
                                                                output.write(buffer, 0, bytesRead)
                                                                bytesDownloaded += bytesRead

                                                                // Emit progress every 500ms to
                                                                // avoid flooding
                                                                val now = System.currentTimeMillis()
                                                                if (now - lastEmitTime > 500) {
                                                                        val progress =
                                                                                bytesDownloaded
                                                                                        .toFloat() /
                                                                                        totalBytes
                                                                        emit(
                                                                                DownloadState
                                                                                        .Downloading(
                                                                                                progress,
                                                                                                bytesDownloaded,
                                                                                                totalBytes
                                                                                        )
                                                                        )
                                                                        lastEmitTime = now
                                                                }
                                                        }
                                                }
                                        }

                                        // Final progress update
                                        emit(
                                                DownloadState.Downloading(
                                                        1f,
                                                        bytesDownloaded,
                                                        totalBytes
                                                )
                                        )

                                        // Rename temp file to final name
                                        if (tempFile.renameTo(outputFile)) {
                                                Log.d(
                                                        TAG,
                                                        "Download complete: ${outputFile.absolutePath} (${bytesDownloaded / 1024 / 1024}MB)"
                                                )
                                                emit(DownloadState.Completed(outputFile))
                                        } else {
                                                throw Exception("Failed to rename downloaded file")
                                        }
                                } catch (e: Exception) {
                                        Log.e(TAG, "Download failed: ${e.message}", e)
                                        tempFile.delete()
                                        emit(DownloadState.Failed(e.message ?: "Download failed"))
                                }
                        }
                        .flowOn(Dispatchers.IO)

        /** Setup SSL to trust all certificates (needed for some emulators) */
        private fun setupTrustAllCerts() {
                try {
                        val trustAllCerts =
                                arrayOf<TrustManager>(
                                        object : X509TrustManager {
                                                override fun checkClientTrusted(
                                                        chain: Array<X509Certificate>,
                                                        authType: String
                                                ) {}
                                                override fun checkServerTrusted(
                                                        chain: Array<X509Certificate>,
                                                        authType: String
                                                ) {}
                                                override fun getAcceptedIssuers():
                                                        Array<X509Certificate> = arrayOf()
                                        }
                                )

                        val sslContext = SSLContext.getInstance("TLS")
                        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
                        HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
                        Log.d(TAG, "SSL trust-all configured for download")
                } catch (e: Exception) {
                        Log.e(TAG, "Failed to setup SSL trust", e)
                }
        }

        /** Delete a downloaded legacy model */
        fun deleteModel(modelId: String): Boolean {
                val modelFile = File(modelsDir, "$modelId.gguf")
                return modelFile.delete()
        }

        /** Get total size of downloaded legacy models */
        fun getTotalDownloadedSize(): Long {
                return getDownloadedModels().sumOf { it.length() }
        }

        /** Clear all downloaded legacy models */
        fun clearAllModels() {
                modelsDir.listFiles()?.forEach { it.delete() }
        }
}
