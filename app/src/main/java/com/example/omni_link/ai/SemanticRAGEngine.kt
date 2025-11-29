package com.example.omni_link.ai

import android.util.Log
import com.cactus.CactusLM
import com.example.omni_link.data.db.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * SemanticRAGEngine - Enhanced Retrieval Augmented Generation with Native Embeddings
 *
 * Uses Cactus SDK's native embedding generation (via llama.cpp bindings) for semantic similarity
 * search. This enables finding contextually relevant information even when exact keywords don't
 * match.
 *
 * Use Cases:
 * 1. Smart Memory Recall - "What was that website I was looking at yesterday about cooking?"
 * 2. Contextual Search - "Find things related to my vacation planning"
 * 3. Semantic Clipboard - Find similar content from clipboard history
 * 4. Activity Pattern Recognition - Understand user behavior semantically
 *
 * Falls back to TF-IDF when embeddings are unavailable (no model loaded, etc.)
 */
class SemanticRAGEngine(
        private val database: OmniLinkDatabase,
        private val cactusLM: CactusLM? = null
) {
    companion object {
        private const val TAG = "SemanticRAG"

        // Context budget
        private const val MAX_CONTEXT_CHARS = 4000

        // Retrieval limits
        private const val MAX_MEMORY_ITEMS = 15
        private const val MAX_CLIPBOARD_ITEMS = 10
        private const val MAX_ACTIVITY_ITEMS = 15
        private const val MAX_SEARCH_ITEMS = 10

        // Embedding config
        private const val EMBEDDING_CACHE_SIZE = 500
        private const val MIN_SEMANTIC_SCORE = 0.3f
        private const val MIN_TFIDF_SCORE = 0.1f

        // Stop words for TF-IDF fallback
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
                        "and",
                        "but",
                        "or",
                        "so",
                        "yet",
                        "both",
                        "not",
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
                        "some",
                        "any",
                        "i",
                        "me",
                        "my",
                        "we",
                        "our",
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
                        "that"
                )
    }

    private val memoryDao = database.memoryDao()
    private val clipboardDao = database.clipboardDao()
    private val activityDao = database.activitySnapshotDao()
    private val searchDao = database.searchHistoryDao()

    // Embedding cache with LRU eviction
    private val embeddingCache =
            LinkedHashMap<String, List<Double>>(EMBEDDING_CACHE_SIZE, 0.75f, true)
    private val cacheMutex = Mutex()

    // Flag to track if embeddings are available
    private var embeddingsAvailable = false

    // ═══════════════════════════════════════════════════════════════════════════════
    // EMBEDDING GENERATION (Native via Cactus SDK)
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Initialize embedding capability. Call this after model is loaded. */
    suspend fun initializeEmbeddings(): Boolean {
        return try {
            // Test embedding generation
            val testResult = cactusLM?.generateEmbedding("test")
            embeddingsAvailable = testResult?.success == true && testResult.embeddings.isNotEmpty()
            Log.d(
                    TAG,
                    "Embeddings initialized: $embeddingsAvailable (dimension: ${testResult?.dimension ?: 0})"
            )
            embeddingsAvailable
        } catch (e: Exception) {
            Log.w(TAG, "Embedding initialization failed, using TF-IDF fallback", e)
            embeddingsAvailable = false
            false
        }
    }

    /**
     * Generate embedding for text using Cactus SDK native bindings. Uses cache to avoid redundant
     * computations.
     */
    private suspend fun getEmbedding(text: String): List<Double>? {
        if (!embeddingsAvailable || cactusLM == null) return null

        val normalizedText = text.trim().take(512) // Limit text length
        if (normalizedText.isBlank()) return null

        // Check cache first
        cacheMutex.withLock {
            embeddingCache[normalizedText]?.let {
                Log.v(TAG, "Embedding cache hit for: ${normalizedText.take(30)}...")
                return it
            }
        }

        return try {
            val result = cactusLM.generateEmbedding(normalizedText)
            if (result?.success == true && result.embeddings.isNotEmpty()) {
                // Add to cache with LRU eviction
                cacheMutex.withLock {
                    if (embeddingCache.size >= EMBEDDING_CACHE_SIZE) {
                        val firstKey = embeddingCache.keys.first()
                        embeddingCache.remove(firstKey)
                    }
                    embeddingCache[normalizedText] = result.embeddings
                }
                Log.v(
                        TAG,
                        "Generated embedding (dim=${result.dimension}) for: ${normalizedText.take(30)}..."
                )
                result.embeddings
            } else {
                Log.w(TAG, "Embedding generation returned empty/failed")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Embedding generation error: ${e.message}")
            null
        }
    }

    /**
     * Calculate cosine similarity between two embedding vectors. Returns a value between -1 and 1
     * (higher = more similar).
     */
    private fun cosineSimilarity(a: List<Double>, b: List<Double>): Float {
        if (a.size != b.size || a.isEmpty()) return 0f

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) (dotProduct / denominator).toFloat() else 0f
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MAIN SEMANTIC RETRIEVAL
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Semantic retrieval - find contextually relevant information for a query. Uses embeddings when
     * available, falls back to TF-IDF otherwise.
     */
    suspend fun semanticRetrieve(
            query: String,
            includeRecall: Boolean = true,
            maxContextChars: Int = MAX_CONTEXT_CHARS
    ): SemanticRAGContext =
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Semantic retrieval for: $query (embeddings: $embeddingsAvailable)")

                // Generate query embedding
                val queryEmbedding = getEmbedding(query)
                val keywords = extractKeywords(query)

                Log.d(
                        TAG,
                        "Query embedding: ${if (queryEmbedding != null) "generated" else "failed/unavailable"}"
                )
                Log.d(TAG, "Keywords: $keywords")

                // Retrieve from each source with semantic scoring
                val memories = retrieveMemoriesSemantic(query, queryEmbedding, keywords)
                val clipboard =
                        if (includeRecall)
                                retrieveClipboardSemantic(query, queryEmbedding, keywords)
                        else emptyList()
                val activities =
                        if (includeRecall)
                                retrieveActivitiesSemantic(query, queryEmbedding, keywords)
                        else emptyList()
                val searches =
                        if (includeRecall) retrieveSearchesSemantic(query, queryEmbedding, keywords)
                        else emptyList()

                // Build context string
                val contextString =
                        buildSemanticContextString(
                                query = query,
                                memories = memories,
                                clipboard = clipboard,
                                activities = activities,
                                searches = searches,
                                maxChars = maxContextChars
                        )

                Log.d(
                        TAG,
                        "Retrieved: ${memories.size} memories, ${clipboard.size} clips, ${activities.size} activities, ${searches.size} searches"
                )

                SemanticRAGContext(
                        query = query,
                        keywords = keywords,
                        memories = memories,
                        clipboard = clipboard,
                        activities = activities,
                        searches = searches,
                        contextString = contextString,
                        usedSemanticSearch = queryEmbedding != null
                )
            }

    // ═══════════════════════════════════════════════════════════════════════════════
    // SOURCE-SPECIFIC SEMANTIC RETRIEVAL
    // ═══════════════════════════════════════════════════════════════════════════════

    private suspend fun retrieveMemoriesSemantic(
            query: String,
            queryEmbedding: List<Double>?,
            keywords: List<String>
    ): List<SemanticRankedMemory> {
        val allMemories = memoryDao.getTopMemories(50)

        return allMemories
                .mapNotNull { memory ->
                    val text = "${memory.key} ${memory.value}"
                    val score =
                            calculateSemanticScore(
                                    queryEmbedding = queryEmbedding,
                                    query = query,
                                    keywords = keywords,
                                    text = text,
                                    boost = memory.importance / 10f + memory.accessCount / 100f
                            )

                    val minScore =
                            if (queryEmbedding != null) MIN_SEMANTIC_SCORE else MIN_TFIDF_SCORE
                    if (score >= minScore) {
                        SemanticRankedMemory(memory, score, queryEmbedding != null)
                    } else null
                }
                .sortedByDescending { it.score }
                .take(MAX_MEMORY_ITEMS)
    }

    private suspend fun retrieveClipboardSemantic(
            query: String,
            queryEmbedding: List<Double>?,
            keywords: List<String>
    ): List<SemanticRankedClipboard> {
        val recentClips = clipboardDao.getRecentClips(30)
        val searchedClips = keywords.flatMap { clipboardDao.searchClips(it, 15) }
        val allClips = (recentClips + searchedClips).distinctBy { it.id }

        return allClips
                .mapNotNull { clip ->
                    val score =
                            calculateSemanticScore(
                                    queryEmbedding = queryEmbedding,
                                    query = query,
                                    keywords = keywords,
                                    text = clip.content,
                                    boost = if (clip.isPinned) 0.2f else 0f,
                                    recencyBoost = calculateRecencyBoost(clip.timestamp)
                            )

                    val minScore =
                            if (queryEmbedding != null) MIN_SEMANTIC_SCORE else MIN_TFIDF_SCORE
                    if (score >= minScore) {
                        SemanticRankedClipboard(clip, score, queryEmbedding != null)
                    } else null
                }
                .sortedByDescending { it.score }
                .take(MAX_CLIPBOARD_ITEMS)
    }

    private suspend fun retrieveActivitiesSemantic(
            query: String,
            queryEmbedding: List<Double>?,
            keywords: List<String>
    ): List<SemanticRankedActivity> {
        val recentActivities = activityDao.getRecentSnapshots(50)
        val searchedActivities = keywords.flatMap { activityDao.searchSnapshots(it, 30) }
        val allActivities = (recentActivities + searchedActivities).distinctBy { it.id }

        return allActivities
                .mapNotNull { activity ->
                    val text =
                            "${activity.appName} ${activity.screenTitle ?: ""} ${activity.visibleText}"
                    val score =
                            calculateSemanticScore(
                                    queryEmbedding = queryEmbedding,
                                    query = query,
                                    keywords = keywords,
                                    text = text,
                                    recencyBoost = calculateRecencyBoost(activity.timestamp)
                            )

                    val minScore =
                            if (queryEmbedding != null) MIN_SEMANTIC_SCORE else MIN_TFIDF_SCORE
                    if (score >= minScore) {
                        SemanticRankedActivity(activity, score, queryEmbedding != null)
                    } else null
                }
                .sortedByDescending { it.score }
                .take(MAX_ACTIVITY_ITEMS)
    }

    private suspend fun retrieveSearchesSemantic(
            query: String,
            queryEmbedding: List<Double>?,
            keywords: List<String>
    ): List<SemanticRankedSearch> {
        val recentSearches = searchDao.getRecentSearches(30)
        val searchedQueries = keywords.flatMap { searchDao.searchQueries(it, 15) }
        val allSearches = (recentSearches + searchedQueries).distinctBy { it.id }

        return allSearches
                .mapNotNull { search ->
                    val score =
                            calculateSemanticScore(
                                    queryEmbedding = queryEmbedding,
                                    query = query,
                                    keywords = keywords,
                                    text = search.query,
                                    recencyBoost = calculateRecencyBoost(search.timestamp)
                            )

                    val minScore =
                            if (queryEmbedding != null) MIN_SEMANTIC_SCORE else MIN_TFIDF_SCORE
                    if (score >= minScore) {
                        SemanticRankedSearch(search, score, queryEmbedding != null)
                    } else null
                }
                .sortedByDescending { it.score }
                .take(MAX_SEARCH_ITEMS)
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // SCORING
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Calculate semantic score using embeddings when available, TF-IDF otherwise. */
    private suspend fun calculateSemanticScore(
            queryEmbedding: List<Double>?,
            query: String,
            keywords: List<String>,
            text: String,
            boost: Float = 0f,
            recencyBoost: Float = 0f
    ): Float {
        if (text.isBlank()) return 0f

        var score = 0f

        // Try semantic similarity first
        if (queryEmbedding != null) {
            val textEmbedding = getEmbedding(text)
            if (textEmbedding != null) {
                // Cosine similarity ranges from -1 to 1, normalize to 0-1
                val similarity = (cosineSimilarity(queryEmbedding, textEmbedding) + 1f) / 2f
                score = similarity * 0.8f // Semantic weight
            }
        }

        // Add TF-IDF component (always included for keyword boost)
        val tfidfScore = calculateTFIDFScore(query, keywords, text)
        score += tfidfScore * 0.2f

        // Apply boosts
        score += boost
        score += recencyBoost

        return score.coerceIn(0f, 1f)
    }

    /** TF-IDF scoring for keyword matching. */
    private fun calculateTFIDFScore(query: String, keywords: List<String>, text: String): Float {
        if (text.isBlank()) return 0f

        val textLower = text.lowercase()
        val textTokens = textLower.split(Regex("\\W+")).filter { it.length > 2 }
        if (textTokens.isEmpty()) return 0f

        var score = 0f

        // Exact phrase match (high weight)
        val queryLower = query.lowercase()
        if (textLower.contains(queryLower)) {
            score += 0.5f
        }

        // Keyword matching
        var keywordMatches = 0
        for (keyword in keywords) {
            val count = textTokens.count { it == keyword || it.contains(keyword) }
            if (count > 0) {
                keywordMatches++
                val tf = count.toFloat() / textTokens.size
                score += tf * 0.1f
            }
        }

        // Keyword coverage bonus
        if (keywords.isNotEmpty()) {
            score += (keywordMatches.toFloat() / keywords.size) * 0.3f
        }

        return score.coerceIn(0f, 1f)
    }

    private fun calculateRecencyBoost(timestamp: Long): Float {
        val ageHours = (System.currentTimeMillis() - timestamp) / 3600_000f
        return when {
            ageHours < 1 -> 0.15f
            ageHours < 4 -> 0.12f
            ageHours < 24 -> 0.08f
            ageHours < 168 -> 0.04f
            else -> 0f
        }
    }

    private fun extractKeywords(query: String): List<String> {
        return query.lowercase()
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .split(Regex("\\s+"))
                .filter { it.length > 2 && it !in STOP_WORDS }
                .distinct()
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // CONTEXT BUILDING
    // ═══════════════════════════════════════════════════════════════════════════════

    private fun buildSemanticContextString(
            query: String,
            memories: List<SemanticRankedMemory>,
            clipboard: List<SemanticRankedClipboard>,
            activities: List<SemanticRankedActivity>,
            searches: List<SemanticRankedSearch>,
            maxChars: Int
    ): String {
        val builder = StringBuilder()
        var remainingChars = maxChars

        // 1. Memories (most important)
        if (memories.isNotEmpty() && remainingChars > 200) {
            builder.appendLine("## Remembered Information:")
            for (memory in memories) {
                val scoreIndicator = if (memory.usedSemantic) "🧠" else "📝"
                val line = "- $scoreIndicator ${memory.item.key}: ${memory.item.value}"
                if (line.length < remainingChars - 50) {
                    builder.appendLine(line)
                    remainingChars -= line.length + 1
                }
            }
            builder.appendLine()
        }

        // 2. Recent relevant activity
        if (activities.isNotEmpty() && remainingChars > 200) {
            builder.appendLine("## Related Activity:")
            for (activity in activities.take(8)) {
                val time = formatRelativeTime(activity.item.timestamp)
                val title = activity.item.screenTitle ?: activity.item.activityType
                val scoreIndicator = if (activity.usedSemantic) "🧠" else "📝"
                val line = "- $scoreIndicator [$time] ${activity.item.appName}: $title"
                if (line.length < remainingChars - 50) {
                    builder.appendLine(line)
                    remainingChars -= line.length + 1
                }
            }
            builder.appendLine()
        }

        // 3. Relevant clipboard
        if (clipboard.isNotEmpty() && remainingChars > 200) {
            builder.appendLine("## Related from Clipboard:")
            for (clip in clipboard.take(5)) {
                val time = formatRelativeTime(clip.item.timestamp)
                val content = clip.item.content.take(150)
                val scoreIndicator = if (clip.usedSemantic) "🧠" else "📝"
                val line =
                        "- $scoreIndicator [$time] $content${if (clip.item.content.length > 150) "..." else ""}"
                if (line.length < remainingChars - 50) {
                    builder.appendLine(line)
                    remainingChars -= line.length + 1
                }
            }
            builder.appendLine()
        }

        // 4. Recent searches
        if (searches.isNotEmpty() && remainingChars > 100) {
            builder.appendLine("## Related Searches:")
            for (search in searches.take(5)) {
                val scoreIndicator = if (search.usedSemantic) "🧠" else "📝"
                val line =
                        "- $scoreIndicator \"${search.item.query}\" in ${search.item.sourceApp.substringAfterLast(".")}"
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
    // USE CASE: SMART MEMORY RECALL
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Smart Memory Recall - Answer questions about past activity using semantic search.
     *
     * Examples:
     * - "What was that recipe website I was looking at?"
     * - "Find things related to my vacation planning"
     * - "What did I copy about the meeting?"
     * - "Show me stuff about machine learning"
     */
    suspend fun smartRecall(query: String): SmartRecallResult =
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Smart recall query: $query")

                val context = semanticRetrieve(query, includeRecall = true)

                // Generate a natural language summary
                val summary = generateRecallSummary(query, context)

                SmartRecallResult(
                        query = query,
                        summary = summary,
                        context = context,
                        totalMatches = context.totalItems,
                        usedSemanticSearch = context.usedSemanticSearch
                )
            }

    private fun generateRecallSummary(query: String, context: SemanticRAGContext): String {
        if (context.isEmpty) {
            return "I couldn't find anything related to \"$query\" in your history. Try a different search or check if you have activity history enabled."
        }

        val builder = StringBuilder()
        val searchType = if (context.usedSemanticSearch) "semantic" else "keyword"

        builder.appendLine("Found ${context.totalItems} related items using $searchType search:\n")

        // Top memories
        if (context.memories.isNotEmpty()) {
            builder.appendLine("**💡 Remembered Info:**")
            context.memories.take(3).forEach { memory ->
                builder.appendLine(
                        "• ${memory.item.key}: ${memory.item.value.take(100)}${if (memory.item.value.length > 100) "..." else ""}"
                )
            }
            builder.appendLine()
        }

        // Top activities
        if (context.activities.isNotEmpty()) {
            builder.appendLine("**📱 Related Activity:**")
            context.activities.take(3).forEach { activity ->
                val time = formatRelativeTime(activity.item.timestamp)
                val title = activity.item.screenTitle ?: activity.item.activityType
                builder.appendLine("• [$time] ${activity.item.appName}: $title")
            }
            builder.appendLine()
        }

        // Top clipboard
        if (context.clipboard.isNotEmpty()) {
            builder.appendLine("**📋 From Clipboard:**")
            context.clipboard.take(2).forEach { clip ->
                val time = formatRelativeTime(clip.item.timestamp)
                val content = clip.item.content.take(80)
                builder.appendLine(
                        "• [$time] $content${if (clip.item.content.length > 80) "..." else ""}"
                )
            }
            builder.appendLine()
        }

        // Top searches
        if (context.searches.isNotEmpty()) {
            builder.appendLine("**🔍 Related Searches:**")
            context.searches.take(2).forEach { search ->
                builder.appendLine("• \"${search.item.query}\"")
            }
        }

        return builder.toString().trim()
    }

    /**
     * Find semantically similar content to a given text. Useful for "find similar" functionality.
     */
    suspend fun findSimilar(text: String, limit: Int = 10): List<SimilarContent> =
            withContext(Dispatchers.IO) {
                val embedding = getEmbedding(text) ?: return@withContext emptyList()
                val results = mutableListOf<SimilarContent>()

                // Search through recent activities
                val activities = activityDao.getRecentSnapshots(100)
                for (activity in activities) {
                    val activityText =
                            "${activity.appName} ${activity.screenTitle ?: ""} ${activity.visibleText}"
                    val activityEmbedding = getEmbedding(activityText)
                    if (activityEmbedding != null) {
                        val similarity = cosineSimilarity(embedding, activityEmbedding)
                        if (similarity > MIN_SEMANTIC_SCORE) {
                            results.add(
                                    SimilarContent(
                                            type = ContentType.ACTIVITY,
                                            title =
                                                    "${activity.appName}: ${activity.screenTitle ?: activity.activityType}",
                                            preview = activity.visibleText.take(150),
                                            similarity = similarity,
                                            timestamp = activity.timestamp
                                    )
                            )
                        }
                    }
                }

                // Search through clipboard
                val clips = clipboardDao.getRecentClips(50)
                for (clip in clips) {
                    val clipEmbedding = getEmbedding(clip.content)
                    if (clipEmbedding != null) {
                        val similarity = cosineSimilarity(embedding, clipEmbedding)
                        if (similarity > MIN_SEMANTIC_SCORE) {
                            results.add(
                                    SimilarContent(
                                            type = ContentType.CLIPBOARD,
                                            title = "Clipboard",
                                            preview = clip.content.take(150),
                                            similarity = similarity,
                                            timestamp = clip.timestamp
                                    )
                            )
                        }
                    }
                }

                results.sortedByDescending { it.similarity }.take(limit)
            }

    /** Clear embedding cache */
    suspend fun clearCache() {
        cacheMutex.withLock { embeddingCache.clear() }
        Log.d(TAG, "Embedding cache cleared")
    }

    /** Get cache statistics */
    suspend fun getCacheStats(): EmbeddingCacheStats {
        return cacheMutex.withLock {
            EmbeddingCacheStats(
                    size = embeddingCache.size,
                    maxSize = EMBEDDING_CACHE_SIZE,
                    embeddingsAvailable = embeddingsAvailable
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ═══════════════════════════════════════════════════════════════════════════════

data class SemanticRAGContext(
        val query: String,
        val keywords: List<String>,
        val memories: List<SemanticRankedMemory>,
        val clipboard: List<SemanticRankedClipboard>,
        val activities: List<SemanticRankedActivity>,
        val searches: List<SemanticRankedSearch>,
        val contextString: String,
        val usedSemanticSearch: Boolean
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

data class SemanticRankedMemory(
        val item: MemoryEntity,
        val score: Float,
        val usedSemantic: Boolean
)

data class SemanticRankedClipboard(
        val item: ClipboardEntry,
        val score: Float,
        val usedSemantic: Boolean
)

data class SemanticRankedActivity(
        val item: ActivitySnapshot,
        val score: Float,
        val usedSemantic: Boolean
)

data class SemanticRankedSearch(val item: SearchEntry, val score: Float, val usedSemantic: Boolean)

data class SmartRecallResult(
        val query: String,
        val summary: String,
        val context: SemanticRAGContext,
        val totalMatches: Int,
        val usedSemanticSearch: Boolean
)

data class SimilarContent(
        val type: ContentType,
        val title: String,
        val preview: String,
        val similarity: Float,
        val timestamp: Long
)

enum class ContentType {
    ACTIVITY,
    CLIPBOARD,
    MEMORY,
    SEARCH
}

data class EmbeddingCacheStats(val size: Int, val maxSize: Int, val embeddingsAvailable: Boolean)





