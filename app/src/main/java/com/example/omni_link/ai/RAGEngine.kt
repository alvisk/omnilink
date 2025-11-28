package com.example.omni_link.ai

import android.util.Log
import com.example.omni_link.data.db.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ln
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * RAGEngine - Retrieval Augmented Generation for on-device AI
 *
 * Track 1: Memory Master - Intelligent context retrieval for local LLMs
 *
 * Features:
 * - TF-IDF based text similarity (no external embeddings needed)
 * - Multi-source retrieval (memories, clipboard, activity, searches)
 * - Query expansion and keyword extraction
 * - Ranked results with relevance scoring
 * - Context window optimization for small LLMs
 */
class RAGEngine(private val database: OmniLinkDatabase, private val recallManager: RecallManager) {
    companion object {
        private const val TAG = "RAGEngine"

        // Context budget (approximate tokens, assuming ~4 chars per token)
        private const val MAX_CONTEXT_CHARS = 3000

        // Retrieval limits
        private const val MAX_MEMORY_ITEMS = 10
        private const val MAX_CLIPBOARD_ITEMS = 5
        private const val MAX_ACTIVITY_ITEMS = 8
        private const val MAX_SEARCH_ITEMS = 5

        // Relevance thresholds
        private const val MIN_RELEVANCE_SCORE = 0.1f

        // Common stop words to ignore in search
        private val STOP_WORDS =
                setOf(
                        "a",
                        "an",
                        "the",
                        "is",
                        "are",
                        "was",
                        "were",
                        "be",
                        "been",
                        "being",
                        "have",
                        "has",
                        "had",
                        "do",
                        "does",
                        "did",
                        "will",
                        "would",
                        "could",
                        "should",
                        "may",
                        "might",
                        "must",
                        "shall",
                        "can",
                        "need",
                        "dare",
                        "ought",
                        "used",
                        "to",
                        "of",
                        "in",
                        "for",
                        "on",
                        "with",
                        "at",
                        "by",
                        "from",
                        "up",
                        "about",
                        "into",
                        "over",
                        "after",
                        "beneath",
                        "under",
                        "above",
                        "and",
                        "but",
                        "or",
                        "nor",
                        "so",
                        "yet",
                        "both",
                        "either",
                        "neither",
                        "not",
                        "only",
                        "own",
                        "same",
                        "than",
                        "too",
                        "very",
                        "just",
                        "also",
                        "now",
                        "here",
                        "there",
                        "when",
                        "where",
                        "why",
                        "how",
                        "all",
                        "each",
                        "every",
                        "both",
                        "few",
                        "more",
                        "most",
                        "other",
                        "some",
                        "such",
                        "no",
                        "any",
                        "i",
                        "me",
                        "my",
                        "myself",
                        "we",
                        "our",
                        "ours",
                        "you",
                        "your",
                        "he",
                        "him",
                        "his",
                        "she",
                        "her",
                        "it",
                        "its",
                        "they",
                        "them",
                        "their",
                        "what",
                        "which",
                        "who",
                        "this",
                        "that",
                        "these",
                        "those",
                        "am",
                        "been",
                        "being"
                )
    }

    private val memoryDao = database.memoryDao()
    private val clipboardDao = database.clipboardDao()
    private val activityDao = database.activitySnapshotDao()
    private val searchDao = database.searchHistoryDao()

    // ═══════════════════════════════════════════════════════════════════════════════
    // MAIN RETRIEVAL METHOD
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Retrieve relevant context for a user query
     *
     * @param query The user's query
     * @param includeRecall Whether to include activity/clipboard history
     * @param maxContextChars Maximum characters for context
     * @return RAGContext with retrieved items and formatted context string
     */
    suspend fun retrieveContext(
            query: String,
            includeRecall: Boolean = true,
            maxContextChars: Int = MAX_CONTEXT_CHARS
    ): RAGContext =
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Retrieving context for: $query")

                // Extract keywords from query
                val keywords = extractKeywords(query)
                Log.d(TAG, "Keywords: $keywords")

                // Retrieve from each source
                val memories = retrieveMemories(query, keywords)
                val clipboard =
                        if (includeRecall) retrieveClipboard(query, keywords) else emptyList()
                val activities =
                        if (includeRecall) retrieveActivities(query, keywords) else emptyList()
                val searches = if (includeRecall) retrieveSearches(query, keywords) else emptyList()

                // Build context string within budget
                val contextString =
                        buildContextString(
                                query = query,
                                memories = memories,
                                clipboard = clipboard,
                                activities = activities,
                                searches = searches,
                                maxChars = maxContextChars
                        )

                RAGContext(
                        query = query,
                        keywords = keywords,
                        memories = memories,
                        clipboard = clipboard,
                        activities = activities,
                        searches = searches,
                        contextString = contextString
                )
            }

    // ═══════════════════════════════════════════════════════════════════════════════
    // KEYWORD EXTRACTION
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Extract meaningful keywords from query */
    private fun extractKeywords(query: String): List<String> {
        return query.lowercase()
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .split(Regex("\\s+"))
                .filter { it.length > 2 && it !in STOP_WORDS }
                .distinct()
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // SOURCE-SPECIFIC RETRIEVAL
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Retrieve relevant memories */
    private suspend fun retrieveMemories(
            query: String,
            keywords: List<String>
    ): List<RankedMemory> {
        val allMemories = memoryDao.getTopMemories(50)

        return allMemories
                .map { memory ->
                    val score =
                            calculateRelevance(
                                    query = query,
                                    keywords = keywords,
                                    text = "${memory.key} ${memory.value}",
                                    boost = memory.importance / 10f + memory.accessCount / 100f
                            )
                    RankedMemory(memory, score)
                }
                .filter { it.score >= MIN_RELEVANCE_SCORE }
                .sortedByDescending { it.score }
                .take(MAX_MEMORY_ITEMS)
    }

    /** Retrieve relevant clipboard entries */
    private suspend fun retrieveClipboard(
            query: String,
            keywords: List<String>
    ): List<RankedClipboard> {
        // Get recent clips + search for matching ones
        val recentClips = clipboardDao.getRecentClips(20)
        val searchedClips = keywords.flatMap { clipboardDao.searchClips(it, 10) }
        val allClips = (recentClips + searchedClips).distinctBy { it.id }

        return allClips
                .map { clip ->
                    val score =
                            calculateRelevance(
                                    query = query,
                                    keywords = keywords,
                                    text = clip.content,
                                    boost = if (clip.isPinned) 0.3f else 0f,
                                    recencyBoost = calculateRecencyBoost(clip.timestamp)
                            )
                    RankedClipboard(clip, score)
                }
                .filter { it.score >= MIN_RELEVANCE_SCORE }
                .sortedByDescending { it.score }
                .take(MAX_CLIPBOARD_ITEMS)
    }

    /** Retrieve relevant activity snapshots */
    private suspend fun retrieveActivities(
            query: String,
            keywords: List<String>
    ): List<RankedActivity> {
        // Get recent activities + search
        val recentActivities = activityDao.getRecentSnapshots(30)
        val searchedActivities = keywords.flatMap { activityDao.searchSnapshots(it, 20) }
        val allActivities = (recentActivities + searchedActivities).distinctBy { it.id }

        return allActivities
                .map { activity ->
                    val score =
                            calculateRelevance(
                                    query = query,
                                    keywords = keywords,
                                    text =
                                            "${activity.appName} ${activity.screenTitle ?: ""} ${activity.visibleText}",
                                    recencyBoost = calculateRecencyBoost(activity.timestamp)
                            )
                    RankedActivity(activity, score)
                }
                .filter { it.score >= MIN_RELEVANCE_SCORE }
                .sortedByDescending { it.score }
                .take(MAX_ACTIVITY_ITEMS)
    }

    /** Retrieve relevant search queries */
    private suspend fun retrieveSearches(
            query: String,
            keywords: List<String>
    ): List<RankedSearch> {
        val recentSearches = searchDao.getRecentSearches(20)
        val searchedQueries = keywords.flatMap { searchDao.searchQueries(it, 10) }
        val allSearches = (recentSearches + searchedQueries).distinctBy { it.id }

        return allSearches
                .map { search ->
                    val score =
                            calculateRelevance(
                                    query = query,
                                    keywords = keywords,
                                    text = search.query,
                                    recencyBoost = calculateRecencyBoost(search.timestamp)
                            )
                    RankedSearch(search, score)
                }
                .filter { it.score >= MIN_RELEVANCE_SCORE }
                .sortedByDescending { it.score }
                .take(MAX_SEARCH_ITEMS)
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // RELEVANCE SCORING (TF-IDF inspired)
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Calculate relevance score between query and text Uses simplified TF-IDF with keyword matching
     */
    private fun calculateRelevance(
            query: String,
            keywords: List<String>,
            text: String,
            boost: Float = 0f,
            recencyBoost: Float = 0f
    ): Float {
        if (text.isBlank()) return 0f

        val textLower = text.lowercase()
        val textTokens = textLower.split(Regex("\\W+")).filter { it.length > 2 }

        if (textTokens.isEmpty()) return 0f

        // Exact phrase match (highest weight)
        val queryLower = query.lowercase()
        var score = if (textLower.contains(queryLower)) 0.5f else 0f

        // Keyword matching with TF-IDF-like weighting
        var keywordMatches = 0
        for (keyword in keywords) {
            val count = textTokens.count { it == keyword || it.contains(keyword) }
            if (count > 0) {
                keywordMatches++
                // TF component (normalized by doc length)
                val tf = count.toFloat() / textTokens.size
                // IDF component approximation (rarer keywords = higher weight)
                val idf = ln(1000f / (1 + keyword.length))
                score += tf * idf * 0.1f
            }
        }

        // Keyword coverage bonus
        if (keywords.isNotEmpty()) {
            score += (keywordMatches.toFloat() / keywords.size) * 0.3f
        }

        // Apply boosts
        score += boost
        score += recencyBoost

        return score.coerceIn(0f, 1f)
    }

    /** Calculate recency boost (recent items score higher) */
    private fun calculateRecencyBoost(timestamp: Long): Float {
        val ageHours = (System.currentTimeMillis() - timestamp) / 3600_000f
        return when {
            ageHours < 1 -> 0.2f // Last hour
            ageHours < 4 -> 0.15f // Last 4 hours
            ageHours < 24 -> 0.1f // Today
            ageHours < 168 -> 0.05f // This week
            else -> 0f
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // CONTEXT BUILDING
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Build context string from retrieved items, respecting token budget */
    private fun buildContextString(
            query: String,
            memories: List<RankedMemory>,
            clipboard: List<RankedClipboard>,
            activities: List<RankedActivity>,
            searches: List<RankedSearch>,
            maxChars: Int
    ): String {
        val builder = StringBuilder()
        var remainingChars = maxChars

        // Priority order: memories > recent activity > clipboard > searches

        // 1. Memories (most important)
        if (memories.isNotEmpty() && remainingChars > 200) {
            builder.appendLine("## Remembered Information:")
            for (memory in memories) {
                val line = "- ${memory.item.key}: ${memory.item.value}"
                if (line.length < remainingChars - 50) {
                    builder.appendLine(line)
                    remainingChars -= line.length + 1
                }
            }
            builder.appendLine()
        }

        // 2. Recent relevant activity
        if (activities.isNotEmpty() && remainingChars > 200) {
            builder.appendLine("## Recent Activity Context:")
            for (activity in activities.take(5)) {
                val time = formatRelativeTime(activity.item.timestamp)
                val title = activity.item.screenTitle ?: activity.item.activityType
                val line = "- [$time] ${activity.item.appName}: $title"
                if (line.length < remainingChars - 50) {
                    builder.appendLine(line)
                    remainingChars -= line.length + 1
                }
            }
            builder.appendLine()
        }

        // 3. Relevant clipboard
        if (clipboard.isNotEmpty() && remainingChars > 200) {
            builder.appendLine("## Relevant from Clipboard:")
            for (clip in clipboard.take(3)) {
                val time = formatRelativeTime(clip.item.timestamp)
                val content = clip.item.content.take(150)
                val line = "- [$time] $content${if (clip.item.content.length > 150) "..." else ""}"
                if (line.length < remainingChars - 50) {
                    builder.appendLine(line)
                    remainingChars -= line.length + 1
                }
            }
            builder.appendLine()
        }

        // 4. Recent searches
        if (searches.isNotEmpty() && remainingChars > 100) {
            builder.appendLine("## Recent Searches:")
            for (search in searches.take(3)) {
                val line =
                        "- \"${search.item.query}\" in ${search.item.sourceApp.substringAfterLast(".")}"
                if (line.length < remainingChars - 30) {
                    builder.appendLine(line)
                    remainingChars -= line.length + 1
                }
            }
        }

        return builder.toString().trim()
    }

    private fun formatRelativeTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000 -> "just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            else -> SimpleDateFormat("MMM d", Locale.US).format(Date(timestamp))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // QUERY-SPECIFIC RETRIEVAL
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Answer questions about past activity ("What did I search for yesterday?") */
    suspend fun answerRecallQuery(query: String): String =
            withContext(Dispatchers.IO) {
                val queryLower = query.lowercase()

                // Time-based queries
                val timeRange = parseTimeRange(queryLower)

                return@withContext when {
                    // Clipboard queries
                    queryLower.contains("copied") || queryLower.contains("clipboard") -> {
                        val clips =
                                if (timeRange != null) {
                                    clipboardDao.getClipsSince(timeRange.first)
                                } else {
                                    clipboardDao.getRecentClips(10)
                                }
                        formatClipboardAnswer(clips)
                    }

                    // Search queries
                    queryLower.contains("search") ||
                            queryLower.contains("looked up") ||
                            queryLower.contains("googled") -> {
                        val searches =
                                if (timeRange != null) {
                                    searchDao.getSearchesSince(timeRange.first)
                                } else {
                                    searchDao.getRecentSearches(10)
                                }
                        formatSearchAnswer(searches)
                    }

                    // App usage queries
                    queryLower.contains("used") ||
                            queryLower.contains("app") ||
                            queryLower.contains("time spent") -> {
                        val usage = database.appUsageDao().getMostUsedApps(10)
                        formatUsageAnswer(usage)
                    }

                    // Activity queries
                    queryLower.contains("doing") ||
                            queryLower.contains("looking at") ||
                            queryLower.contains("visited") -> {
                        val activities =
                                if (timeRange != null) {
                                    activityDao.getSnapshotsSince(timeRange.first)
                                } else {
                                    activityDao.getRecentSnapshots(20)
                                }
                        formatActivityAnswer(activities)
                    }

                    // General recall
                    else -> {
                        val context = retrieveContext(query)
                        if (context.isEmpty) {
                            "I don't have any relevant information stored about that."
                        } else {
                            "Based on your activity:\n\n${context.contextString}"
                        }
                    }
                }
            }

    /** Parse time range from natural language */
    private fun parseTimeRange(query: String): Pair<Long, Long>? {
        val now = System.currentTimeMillis()
        val hour = 3600_000L
        val day = 24 * hour

        return when {
            query.contains("yesterday") -> Pair(now - 2 * day, now - day)
            query.contains("today") -> Pair(now - day, now)
            query.contains("this week") -> Pair(now - 7 * day, now)
            query.contains("last hour") -> Pair(now - hour, now)
            query.contains("last") && query.contains("hour") -> {
                val hours =
                        Regex("(\\d+)\\s*hour").find(query)?.groupValues?.get(1)?.toLongOrNull()
                                ?: 1
                Pair(now - hours * hour, now)
            }
            query.contains("last") && query.contains("day") -> {
                val days =
                        Regex("(\\d+)\\s*day").find(query)?.groupValues?.get(1)?.toLongOrNull() ?: 1
                Pair(now - days * day, now)
            }
            else -> null
        }
    }

    private fun formatClipboardAnswer(clips: List<ClipboardEntry>): String {
        if (clips.isEmpty()) return "I don't have any clipboard history for that time period."

        return buildString {
            appendLine("Here's what you copied recently:")
            clips.take(10).forEach { clip ->
                val time = formatRelativeTime(clip.timestamp)
                val content = clip.content.take(100)
                appendLine("• [$time] $content${if (clip.content.length > 100) "..." else ""}")
            }
        }
    }

    private fun formatSearchAnswer(searches: List<SearchEntry>): String {
        if (searches.isEmpty()) return "I don't have any search history for that time period."

        return buildString {
            appendLine("Here's what you searched for:")
            searches.take(10).forEach { search ->
                val time = formatRelativeTime(search.timestamp)
                val app = search.sourceApp.substringAfterLast(".")
                appendLine("• [$time] \"${search.query}\" in $app")
            }
        }
    }

    private fun formatUsageAnswer(usage: List<AppUsageSummary>): String {
        if (usage.isEmpty()) return "I don't have any app usage data yet."

        return buildString {
            appendLine("Your most used apps:")
            usage.take(10).forEach { app ->
                val minutes = app.totalDurationMs / 60_000
                appendLine("• ${app.appName}: ${minutes}min (${app.openCount} opens)")
            }
        }
    }

    private fun formatActivityAnswer(activities: List<ActivitySnapshot>): String {
        if (activities.isEmpty()) return "I don't have any activity history for that time period."

        return buildString {
            appendLine("Here's what you were doing:")
            activities.take(10).distinctBy { "${it.packageName}-${it.screenTitle}" }.forEach {
                    activity ->
                val time = formatRelativeTime(activity.timestamp)
                val title = activity.screenTitle ?: activity.activityType
                appendLine("• [$time] ${activity.appName}: $title")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ═══════════════════════════════════════════════════════════════════════════════

data class RAGContext(
        val query: String,
        val keywords: List<String>,
        val memories: List<RankedMemory>,
        val clipboard: List<RankedClipboard>,
        val activities: List<RankedActivity>,
        val searches: List<RankedSearch>,
        val contextString: String
) {
    val isEmpty: Boolean
        get() =
                memories.isEmpty() &&
                        clipboard.isEmpty() &&
                        activities.isEmpty() &&
                        searches.isEmpty()

    val totalItems: Int
        get() = memories.size + clipboard.size + activities.size + searches.size
}

data class RankedMemory(val item: MemoryEntity, val score: Float)

data class RankedClipboard(val item: ClipboardEntry, val score: Float)

data class RankedActivity(val item: ActivitySnapshot, val score: Float)

data class RankedSearch(val item: SearchEntry, val score: Float)
