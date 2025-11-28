package com.example.omni_link.ai

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.example.omni_link.data.ScreenState
import com.example.omni_link.data.db.*
import com.google.gson.Gson
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * RecallManager - Microsoft Recall-like activity tracking for on-device RAG
 *
 * Track 1: Memory Master - Provides comprehensive activity history for AI context
 *
 * Features:
 * - Clipboard history with content type detection
 * - App activity snapshots (what user was doing)
 * - Search query tracking
 * - App usage statistics
 * - Automatic cleanup of old data
 */
class RecallManager(private val context: Context, private val database: OmniLinkDatabase) {
    companion object {
        private const val TAG = "RecallManager"

        // Retention periods (in milliseconds)
        private const val CLIPBOARD_RETENTION_DAYS = 30L
        private const val ACTIVITY_RETENTION_DAYS = 14L
        private const val SEARCH_RETENTION_DAYS = 30L
        private const val USAGE_RETENTION_DAYS = 90L

        // Snapshot intervals (to avoid storing too many)
        private const val MIN_SNAPSHOT_INTERVAL_MS = 5000L // 5 seconds minimum between snapshots
        private const val MIN_TEXT_LENGTH_FOR_SNAPSHOT = 20 // Don't snapshot nearly empty screens

        // Content type patterns
        private val URL_PATTERN = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
        private val EMAIL_PATTERN = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")
        private val PHONE_PATTERN = Regex("""\+?[\d\s\-\(\)]{7,15}""")
        private val CODE_PATTERN = Regex("""^\d{4,8}$""") // OTP/verification codes
    }

    private val clipboardDao = database.clipboardDao()
    private val activityDao = database.activitySnapshotDao()
    private val searchDao = database.searchHistoryDao()
    private val appUsageDao = database.appUsageDao()

    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // Track state for deduplication
    private var lastClipboardHash: String? = null
    private var lastSnapshotTime = 0L
    private var lastPackageName: String? = null
    private var lastAppOpenTime = 0L

    // Clipboard listener
    private var clipboardManager: ClipboardManager? = null
    private val clipboardListener =
            ClipboardManager.OnPrimaryClipChangedListener {
                CoroutineScope(Dispatchers.IO).launch { captureClipboard() }
            }

    // ═══════════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Start monitoring clipboard changes */
    fun startClipboardMonitoring() {
        try {
            clipboardManager =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
            Log.d(TAG, "Clipboard monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start clipboard monitoring", e)
        }
    }

    /** Stop monitoring clipboard changes */
    fun stopClipboardMonitoring() {
        try {
            clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
            clipboardManager = null
            Log.d(TAG, "Clipboard monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping clipboard monitoring", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // CLIPBOARD TRACKING
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Capture current clipboard content */
    suspend fun captureClipboard(sourceApp: String? = null) {
        try {
            val clipboard = clipboardManager ?: return
            val clip = clipboard.primaryClip ?: return

            if (clip.itemCount == 0) return

            val text = clip.getItemAt(0).text?.toString() ?: return
            if (text.isBlank() || text.length > 10000) return // Skip empty or huge clips

            // Compute hash to avoid duplicates
            val hash = computeHash(text)
            if (hash == lastClipboardHash) return // Same content, skip

            // Check if already exists
            val existing = clipboardDao.getByHash(hash)
            if (existing != null) {
                // Update access count instead of creating duplicate
                clipboardDao.incrementAccessCount(existing.id)
                lastClipboardHash = hash
                return
            }

            // Detect content type
            val contentType = detectContentType(text)

            val entry =
                    ClipboardEntry(
                            content = text,
                            contentHash = hash,
                            contentType = contentType,
                            sourceApp = sourceApp ?: lastPackageName
                    )

            clipboardDao.insert(entry)
            lastClipboardHash = hash

            Log.d(TAG, "Captured clipboard: ${text.take(50)}... (type: $contentType)")
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing clipboard", e)
        }
    }

    /** Detect the type of clipboard content */
    private fun detectContentType(text: String): String {
        val trimmed = text.trim()
        return when {
            URL_PATTERN.containsMatchIn(trimmed) -> "url"
            EMAIL_PATTERN.containsMatchIn(trimmed) -> "email"
            PHONE_PATTERN.matches(trimmed) -> "phone"
            CODE_PATTERN.matches(trimmed) -> "code"
            trimmed.contains("\n") && trimmed.lines().size > 3 -> "multiline"
            trimmed.length > 200 -> "long_text"
            else -> "text"
        }
    }

    /** Get recent clipboard entries for RAG context */
    suspend fun getRecentClipboard(limit: Int = 10): List<ClipboardEntry> {
        return clipboardDao.getRecentClips(limit)
    }

    /** Search clipboard history */
    suspend fun searchClipboard(query: String): List<ClipboardEntry> {
        return clipboardDao.searchClips(query)
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // ACTIVITY SNAPSHOT TRACKING
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Capture an activity snapshot from current screen state */
    suspend fun captureActivitySnapshot(screenState: ScreenState, appName: String? = null) {
        val now = System.currentTimeMillis()

        // Rate limiting - don't capture too frequently
        if (now - lastSnapshotTime < MIN_SNAPSHOT_INTERVAL_MS) return

        // Don't capture our own app
        val packageName = screenState.packageName ?: return
        if (packageName.contains("omni_link")) return

        // Extract visible text
        val visibleText = extractVisibleText(screenState)
        if (visibleText.length < MIN_TEXT_LENGTH_FOR_SNAPSHOT) return

        // Track app changes for duration calculation
        if (lastPackageName != packageName) {
            // Record duration for previous app
            recordAppUsage(lastPackageName, now - lastAppOpenTime)
            lastPackageName = packageName
            lastAppOpenTime = now
        }

        // Detect activity type
        val activityType = detectActivityType(screenState, visibleText)

        // Extract screen title (first prominent text element)
        val screenTitle = extractScreenTitle(screenState)

        // Extract interactive elements summary
        val interactiveElements = extractInteractiveElementsSummary(screenState)

        // Build metadata
        val metadata = buildMetadata(screenState, visibleText)

        val snapshot =
                ActivitySnapshot(
                        packageName = packageName,
                        appName = appName ?: getAppName(packageName),
                        activityName = screenState.activityName,
                        screenTitle = screenTitle,
                        visibleText = visibleText.take(5000), // Limit size
                        interactiveElements = interactiveElements,
                        activityType = activityType,
                        metadata = metadata
                )

        activityDao.insert(snapshot)
        lastSnapshotTime = now

        // Detect and record searches
        detectAndRecordSearch(screenState, visibleText, packageName)

        Log.d(TAG, "Captured activity: $packageName - $activityType (${visibleText.length} chars)")
    }

    /** Extract all visible text from screen state */
    private fun extractVisibleText(screenState: ScreenState): String {
        val texts = mutableListOf<String>()

        fun extractFromElement(element: com.example.omni_link.data.ScreenElement) {
            element.text?.let { if (it.isNotBlank()) texts.add(it) }
            element.contentDescription?.let {
                if (it.isNotBlank() && it != element.text) texts.add(it)
            }
            element.children.forEach { extractFromElement(it) }
        }

        screenState.elements.forEach { extractFromElement(it) }

        return texts.distinct().joinToString(" | ")
    }

    /** Extract screen title from first prominent element */
    private fun extractScreenTitle(screenState: ScreenState): String? {
        // Look for title-like elements (large text at top, toolbar text, etc.)
        for (element in screenState.elements) {
            val className = element.className.lowercase()
            if (className.contains("toolbar") ||
                            className.contains("actionbar") ||
                            className.contains("title") ||
                            className.contains("header")
            ) {
                element.text?.let { if (it.isNotBlank()) return it }
            }
        }

        // Fallback: first text element
        return screenState.elements.firstNotNullOfOrNull { it.text?.take(100) }
    }

    /** Extract summary of interactive elements */
    private fun extractInteractiveElementsSummary(screenState: ScreenState): String? {
        val clickables = mutableListOf<String>()

        fun extractClickables(element: com.example.omni_link.data.ScreenElement) {
            if (element.isClickable) {
                val label = element.text ?: element.contentDescription
                label?.let { if (it.isNotBlank() && it.length < 50) clickables.add(it) }
            }
            element.children.forEach { extractClickables(it) }
        }

        screenState.elements.forEach { extractClickables(it) }

        return if (clickables.isNotEmpty()) {
            gson.toJson(clickables.distinct().take(20))
        } else null
    }

    /** Detect the type of activity based on screen content */
    private fun detectActivityType(screenState: ScreenState, visibleText: String): String {
        val textLower = visibleText.lowercase()
        val packageLower = (screenState.packageName ?: "").lowercase()

        return when {
            // Search activities
            textLower.contains("search") &&
                    (textLower.contains("results") || textLower.contains("no results")) -> "search"

            // Messaging
            packageLower.contains("message") ||
                    packageLower.contains("whatsapp") ||
                    packageLower.contains("telegram") ||
                    packageLower.contains("messenger") -> "message"

            // Email
            packageLower.contains("mail") || packageLower.contains("gmail") -> "email"

            // Browsing
            packageLower.contains("chrome") ||
                    packageLower.contains("browser") ||
                    packageLower.contains("firefox") ||
                    textLower.contains("http") -> "browse"

            // Social media
            packageLower.contains("twitter") ||
                    packageLower.contains("instagram") ||
                    packageLower.contains("facebook") ||
                    packageLower.contains("tiktok") -> "social"

            // Shopping
            packageLower.contains("amazon") ||
                    packageLower.contains("shop") ||
                    textLower.contains("add to cart") ||
                    textLower.contains("buy now") -> "shop"

            // Media
            packageLower.contains("youtube") ||
                    packageLower.contains("spotify") ||
                    packageLower.contains("netflix") ||
                    packageLower.contains("music") -> "media"

            // Maps/Navigation
            packageLower.contains("maps") || packageLower.contains("navigation") -> "navigate"

            // Settings
            packageLower.contains("settings") -> "settings"
            else -> "view"
        }
    }

    /** Build metadata JSON for additional context */
    private fun buildMetadata(screenState: ScreenState, visibleText: String): String? {
        val metadata = mutableMapOf<String, Any>()

        // Extract URLs
        val urls = URL_PATTERN.findAll(visibleText).map { it.value }.distinct().toList()
        if (urls.isNotEmpty()) metadata["urls"] = urls.take(5)

        // Extract emails
        val emails = EMAIL_PATTERN.findAll(visibleText).map { it.value }.distinct().toList()
        if (emails.isNotEmpty()) metadata["emails"] = emails.take(5)

        // Extract phone numbers
        val phones = PHONE_PATTERN.findAll(visibleText).map { it.value }.distinct().toList()
        if (phones.isNotEmpty()) metadata["phones"] = phones.take(5)

        // Count elements
        metadata["elementCount"] = screenState.flattenElements().size

        return if (metadata.isNotEmpty()) gson.toJson(metadata) else null
    }

    /** Detect and record search queries */
    private suspend fun detectAndRecordSearch(
            screenState: ScreenState,
            visibleText: String,
            packageName: String
    ) {
        // Look for search-related patterns
        val textLower = visibleText.lowercase()

        // Check for search input fields with content
        for (element in screenState.flattenElements()) {
            if (element.isEditable && element.text != null) {
                val text = element.text!!
                // Likely a search box if it has "search" in id or description
                val isSearchBox =
                        element.id?.lowercase()?.contains("search") == true ||
                                element.contentDescription?.lowercase()?.contains("search") == true

                if (isSearchBox && text.isNotBlank() && text.length > 2 && text.length < 200) {
                    val searchType = detectSearchType(packageName)
                    searchDao.insert(
                            SearchEntry(
                                    query = text,
                                    sourceApp = packageName,
                                    searchType = searchType
                            )
                    )
                    Log.d(TAG, "Recorded search: $text (from $packageName)")
                    break
                }
            }
        }
    }

    /** Detect the type of search based on app */
    private fun detectSearchType(packageName: String): String {
        val pkg = packageName.lowercase()
        return when {
            pkg.contains("chrome") || pkg.contains("browser") -> "web"
            pkg.contains("contacts") || pkg.contains("dialer") -> "contact"
            pkg.contains("files") || pkg.contains("documents") -> "file"
            pkg.contains("maps") -> "location"
            pkg.contains("youtube") || pkg.contains("spotify") -> "media"
            pkg.contains("play.store") -> "app"
            else -> "general"
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // APP USAGE TRACKING
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Record app usage duration */
    private suspend fun recordAppUsage(packageName: String?, durationMs: Long) {
        if (packageName == null || durationMs < 1000) return // Ignore < 1 second
        if (packageName.contains("omni_link")) return // Don't track ourselves

        val today = dateFormat.format(Date())
        val existing = appUsageDao.getEntry(packageName, today)

        if (existing != null) {
            appUsageDao.upsert(
                    existing.copy(
                            totalDurationMs = existing.totalDurationMs + durationMs,
                            lastUsed = System.currentTimeMillis()
                    )
            )
        } else {
            appUsageDao.upsert(
                    AppUsageEntry(
                            packageName = packageName,
                            appName = getAppName(packageName),
                            date = today,
                            totalDurationMs = durationMs,
                            openCount = 1
                    )
            )
        }
    }

    /** Record app open event */
    suspend fun recordAppOpen(packageName: String) {
        if (packageName.contains("omni_link")) return

        val today = dateFormat.format(Date())
        val existing = appUsageDao.getEntry(packageName, today)

        if (existing != null) {
            appUsageDao.upsert(
                    existing.copy(
                            openCount = existing.openCount + 1,
                            lastUsed = System.currentTimeMillis()
                    )
            )
        } else {
            appUsageDao.upsert(
                    AppUsageEntry(
                            packageName = packageName,
                            appName = getAppName(packageName),
                            date = today,
                            openCount = 1
                    )
            )
        }
    }

    /** Get most used apps */
    suspend fun getMostUsedApps(limit: Int = 10): List<AppUsageSummary> {
        return appUsageDao.getMostUsedApps(limit)
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // RETRIEVAL METHODS (for RAG)
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Get recent activity for a specific time range */
    suspend fun getRecentActivity(hoursBack: Int = 24, limit: Int = 50): List<ActivitySnapshot> {
        val since = System.currentTimeMillis() - (hoursBack * 60 * 60 * 1000L)
        return activityDao.getSnapshotsSince(since).take(limit)
    }

    /** Search across all recall data */
    suspend fun searchRecall(query: String, limit: Int = 20): RecallSearchResults {
        val clips = clipboardDao.searchClips(query, limit)
        val activities = activityDao.searchSnapshots(query, limit)
        val searches = searchDao.searchQueries(query, limit)

        return RecallSearchResults(
                clipboardMatches = clips,
                activityMatches = activities,
                searchMatches = searches
        )
    }

    /** Get activity timeline for RAG context building */
    suspend fun getActivityTimeline(
            hoursBack: Int = 4,
            maxSnapshots: Int = 20,
            maxClips: Int = 10
    ): RecallTimeline {
        val since = System.currentTimeMillis() - (hoursBack * 60 * 60 * 1000L)

        return RecallTimeline(
                recentActivity = activityDao.getSnapshotsSince(since).take(maxSnapshots),
                recentClipboard = clipboardDao.getClipsSince(since).take(maxClips),
                recentSearches = searchDao.getSearchesSince(since),
                appUsage = appUsageDao.getRecentUsage(10)
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Clean up old data */
    suspend fun cleanupOldData() {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L

        clipboardDao.deleteOldClips(now - CLIPBOARD_RETENTION_DAYS * dayMs)
        activityDao.deleteOldSnapshots(now - ACTIVITY_RETENTION_DAYS * dayMs)
        searchDao.deleteOldSearches(now - SEARCH_RETENTION_DAYS * dayMs)

        val cutoffDate = dateFormat.format(Date(now - USAGE_RETENTION_DAYS * dayMs))
        appUsageDao.deleteOldUsage(cutoffDate)

        Log.d(TAG, "Cleaned up old recall data")
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════════

    private fun computeHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(text.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast(".")
        }
    }
}

/** Search results across all recall data */
data class RecallSearchResults(
        val clipboardMatches: List<ClipboardEntry>,
        val activityMatches: List<ActivitySnapshot>,
        val searchMatches: List<SearchEntry>
) {
    val isEmpty: Boolean
        get() = clipboardMatches.isEmpty() && activityMatches.isEmpty() && searchMatches.isEmpty()

    val totalMatches: Int
        get() = clipboardMatches.size + activityMatches.size + searchMatches.size
}

/** Timeline of recent activity for RAG context */
data class RecallTimeline(
        val recentActivity: List<ActivitySnapshot>,
        val recentClipboard: List<ClipboardEntry>,
        val recentSearches: List<SearchEntry>,
        val appUsage: List<AppUsageEntry>
) {
    /** Convert timeline to context string for LLM */
    fun toContextString(): String = buildString {
        if (recentActivity.isNotEmpty()) {
            appendLine("## Recent Activity:")
            recentActivity.take(10).forEach { activity ->
                appendLine(
                        "- [${formatTime(activity.timestamp)}] ${activity.appName}: ${activity.screenTitle ?: activity.activityType}"
                )
            }
            appendLine()
        }

        if (recentClipboard.isNotEmpty()) {
            appendLine("## Recent Clipboard:")
            recentClipboard.take(5).forEach { clip ->
                appendLine(
                        "- [${formatTime(clip.timestamp)}] ${clip.content.take(100)}${if (clip.content.length > 100) "..." else ""}"
                )
            }
            appendLine()
        }

        if (recentSearches.isNotEmpty()) {
            appendLine("## Recent Searches:")
            recentSearches.take(5).forEach { search ->
                appendLine(
                        "- [${formatTime(search.timestamp)}] \"${search.query}\" in ${search.sourceApp.substringAfterLast(".")}"
                )
            }
            appendLine()
        }

        if (appUsage.isNotEmpty()) {
            appendLine("## Most Used Apps Today:")
            appUsage.take(5).forEach { usage ->
                val minutes = usage.totalDurationMs / 60000
                appendLine("- ${usage.appName}: ${minutes}min (${usage.openCount} opens)")
            }
        }
    }

    private fun formatTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000 -> "just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            else -> SimpleDateFormat("MMM d", Locale.US).format(Date(timestamp))
        }
    }
}
