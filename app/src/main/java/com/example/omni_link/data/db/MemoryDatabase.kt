package com.example.omni_link.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Memory entity - stores persistent knowledge for the AI This is for Track 1: Memory Master */
@Entity(tableName = "memories")
data class MemoryEntity(
        @PrimaryKey val key: String,
        val value: String,
        val category: String, // preference, fact, context, task
        val importance: Int = 5, // 1-10 scale
        val accessCount: Int = 0,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis(),
        val expiresAt: Long? = null // null = never expires
)

/** Conversation message entity - stores chat history */
@Entity(tableName = "messages")
data class MessageEntity(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val role: String, // USER, ASSISTANT, SYSTEM
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        val sessionId: String // Groups messages by session
)

// ═══════════════════════════════════════════════════════════════════════════════
// RECALL / RAG ENTITIES - Microsoft Recall-like activity tracking
// Track 1: Memory Master - Enhanced with clipboard & activity history
// ═══════════════════════════════════════════════════════════════════════════════

/** Clipboard history entry - tracks everything copied to clipboard */
@Entity(
        tableName = "clipboard_history",
        indices = [Index(value = ["timestamp"]), Index(value = ["contentHash"])]
)
data class ClipboardEntry(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val content: String,
        val contentHash: String, // To avoid duplicates
        val contentType: String = "text", // text, url, phone, email, etc.
        val sourceApp: String? = null, // Package name where copied from
        val timestamp: Long = System.currentTimeMillis(),
        val accessCount: Int = 0, // How many times pasted/accessed
        val isPinned: Boolean = false // User can pin important clips
)

/**
 * App activity snapshot - captures what the user was doing in each app Similar to Microsoft Recall
 * - tracks screen content for searchable history
 */
@Entity(
        tableName = "activity_snapshots",
        indices =
                [
                        Index(value = ["timestamp"]),
                        Index(value = ["packageName"]),
                        Index(value = ["activityType"])]
)
data class ActivitySnapshot(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val packageName: String,
        val appName: String,
        val activityName: String? = null,
        val screenTitle: String? = null, // Extracted window title
        val visibleText: String, // Concatenated visible text (searchable)
        val interactiveElements: String? = null, // JSON of clickable elements
        val activityType: String = "view", // view, search, message, browse, etc.
        val timestamp: Long = System.currentTimeMillis(),
        val durationMs: Long = 0, // How long user spent on this screen
        val metadata: String? = null // JSON for extra context (URLs, contact names, etc.)
)

/** Search/query history - what the user searched for across apps */
@Entity(
        tableName = "search_history",
        indices = [Index(value = ["timestamp"]), Index(value = ["query"])]
)
data class SearchEntry(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val query: String,
        val sourceApp: String, // Which app the search was performed in
        val searchType: String = "general", // general, web, contact, file, etc.
        val resultCount: Int? = null,
        val timestamp: Long = System.currentTimeMillis()
)

/** App usage statistics - tracks time spent in apps */
@Entity(
        tableName = "app_usage",
        indices = [Index(value = ["date"]), Index(value = ["packageName"])]
)
data class AppUsageEntry(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val packageName: String,
        val appName: String,
        val date: String, // YYYY-MM-DD format for daily aggregation
        val totalDurationMs: Long = 0,
        val openCount: Int = 0,
        val lastUsed: Long = System.currentTimeMillis()
)

/** Aggregated app usage result (for GROUP BY queries - no id field) */
data class AppUsageSummary(
        val packageName: String,
        val appName: String,
        val totalDurationMs: Long,
        val openCount: Int,
        val lastUsed: Long,
        val date: String
)

/** Data Access Object for memories */
@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY importance DESC, accessCount DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY importance DESC")
    fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE key = :key")
    suspend fun getMemory(key: String): MemoryEntity?

    @Query("SELECT * FROM memories ORDER BY importance DESC, accessCount DESC LIMIT :limit")
    suspend fun getTopMemories(limit: Int): List<MemoryEntity>

    @Query(
            "SELECT * FROM memories WHERE key LIKE '%' || :query || '%' OR value LIKE '%' || :query || '%'"
    )
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMemory(memory: MemoryEntity)

    @Query("UPDATE memories SET accessCount = accessCount + 1, updatedAt = :now WHERE key = :key")
    suspend fun incrementAccessCount(key: String, now: Long = System.currentTimeMillis())

    @Delete suspend fun deleteMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE expiresAt IS NOT NULL AND expiresAt < :now")
    suspend fun deleteExpiredMemories(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM memories") suspend fun clearAllMemories()
}

/** Data Access Object for messages */
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getSessionMessages(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<MessageEntity>

    @Insert suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)

    @Query("DELETE FROM messages") suspend fun clearAllMessages()
}

// ═══════════════════════════════════════════════════════════════════════════════
// RECALL / RAG DAOs
// ═══════════════════════════════════════════════════════════════════════════════

/** Data Access Object for clipboard history */
@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentClips(limit: Int): List<ClipboardEntry>

    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC")
    fun getAllClipsFlow(): Flow<List<ClipboardEntry>>

    @Query("SELECT * FROM clipboard_history WHERE isPinned = 1 ORDER BY timestamp DESC")
    suspend fun getPinnedClips(): List<ClipboardEntry>

    @Query(
            "SELECT * FROM clipboard_history WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun searchClips(query: String, limit: Int = 50): List<ClipboardEntry>

    @Query("SELECT * FROM clipboard_history WHERE contentHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): ClipboardEntry?

    @Query("SELECT * FROM clipboard_history WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getClipsSince(since: Long): List<ClipboardEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entry: ClipboardEntry): Long

    @Update suspend fun update(entry: ClipboardEntry)

    @Query("UPDATE clipboard_history SET accessCount = accessCount + 1 WHERE id = :id")
    suspend fun incrementAccessCount(id: Long)

    @Query("UPDATE clipboard_history SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM clipboard_history WHERE id = :id") suspend fun delete(id: Long)

    @Query("DELETE FROM clipboard_history WHERE timestamp < :before AND isPinned = 0")
    suspend fun deleteOldClips(before: Long)

    @Query("DELETE FROM clipboard_history WHERE isPinned = 0") suspend fun clearUnpinned()
}

/** Data Access Object for activity snapshots */
@Dao
interface ActivitySnapshotDao {
    @Query("SELECT * FROM activity_snapshots ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSnapshots(limit: Int): List<ActivitySnapshot>

    @Query("SELECT * FROM activity_snapshots ORDER BY timestamp DESC")
    fun getAllSnapshotsFlow(): Flow<List<ActivitySnapshot>>

    @Query(
            "SELECT * FROM activity_snapshots WHERE packageName = :packageName ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun getByApp(packageName: String, limit: Int = 50): List<ActivitySnapshot>

    @Query(
            "SELECT * FROM activity_snapshots WHERE visibleText LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun searchSnapshots(query: String, limit: Int = 50): List<ActivitySnapshot>

    @Query(
            "SELECT * FROM activity_snapshots WHERE timestamp >= :since AND timestamp <= :until ORDER BY timestamp DESC"
    )
    suspend fun getSnapshotsBetween(since: Long, until: Long): List<ActivitySnapshot>

    @Query("SELECT * FROM activity_snapshots WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getSnapshotsSince(since: Long): List<ActivitySnapshot>

    @Query(
            "SELECT * FROM activity_snapshots WHERE activityType = :type ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun getByActivityType(type: String, limit: Int = 50): List<ActivitySnapshot>

    @Query("SELECT DISTINCT packageName, appName FROM activity_snapshots ORDER BY timestamp DESC")
    suspend fun getRecentApps(): List<AppInfo>

    @Insert suspend fun insert(snapshot: ActivitySnapshot): Long

    @Query("DELETE FROM activity_snapshots WHERE timestamp < :before")
    suspend fun deleteOldSnapshots(before: Long)

    @Query("DELETE FROM activity_snapshots") suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM activity_snapshots") suspend fun getCount(): Int
}

/** Helper data class for app info queries */
data class AppInfo(val packageName: String, val appName: String)

/** Data Access Object for search history */
@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSearches(limit: Int): List<SearchEntry>

    @Query(
            "SELECT * FROM search_history WHERE sourceApp = :app ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun getSearchesByApp(app: String, limit: Int = 50): List<SearchEntry>

    @Query(
            "SELECT * FROM search_history WHERE query LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun searchQueries(query: String, limit: Int = 50): List<SearchEntry>

    @Query("SELECT * FROM search_history WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getSearchesSince(since: Long): List<SearchEntry>

    @Insert suspend fun insert(entry: SearchEntry): Long

    @Query("DELETE FROM search_history WHERE timestamp < :before")
    suspend fun deleteOldSearches(before: Long)

    @Query("DELETE FROM search_history") suspend fun clearAll()
}

/** Data Access Object for app usage statistics */
@Dao
interface AppUsageDao {
    @Query("SELECT * FROM app_usage WHERE date = :date ORDER BY totalDurationMs DESC")
    suspend fun getUsageForDate(date: String): List<AppUsageEntry>

    @Query("SELECT * FROM app_usage ORDER BY lastUsed DESC LIMIT :limit")
    suspend fun getRecentUsage(limit: Int): List<AppUsageEntry>

    @Query(
            "SELECT * FROM app_usage WHERE packageName = :packageName ORDER BY date DESC LIMIT :limit"
    )
    suspend fun getUsageByApp(packageName: String, limit: Int = 30): List<AppUsageEntry>

    @Query(
            "SELECT packageName, appName, SUM(totalDurationMs) as totalDurationMs, SUM(openCount) as openCount, MAX(lastUsed) as lastUsed, MAX(date) as date FROM app_usage GROUP BY packageName ORDER BY totalDurationMs DESC LIMIT :limit"
    )
    suspend fun getMostUsedApps(limit: Int): List<AppUsageSummary>

    @Query("SELECT * FROM app_usage WHERE packageName = :packageName AND date = :date LIMIT 1")
    suspend fun getEntry(packageName: String, date: String): AppUsageEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entry: AppUsageEntry)

    @Query("DELETE FROM app_usage WHERE date < :before") suspend fun deleteOldUsage(before: String)
}

/**
 * Room database for persistent storage Version 2: Added Recall/RAG tables for clipboard history,
 * activity snapshots, search history, app usage
 */
@Database(
        entities =
                [
                        MemoryEntity::class,
                        MessageEntity::class,
                        ClipboardEntry::class,
                        ActivitySnapshot::class,
                        SearchEntry::class,
                        AppUsageEntry::class],
        version = 2,
        exportSchema = false
)
abstract class OmniLinkDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun messageDao(): MessageDao
    abstract fun clipboardDao(): ClipboardDao
    abstract fun activitySnapshotDao(): ActivitySnapshotDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun appUsageDao(): AppUsageDao

    companion object {
        @Volatile private var INSTANCE: OmniLinkDatabase? = null

        fun getInstance(context: Context): OmniLinkDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance =
                                Room.databaseBuilder(
                                                context.applicationContext,
                                                OmniLinkDatabase::class.java,
                                                "omnilink_db"
                                        )
                                        .fallbackToDestructiveMigration() // For hackathon -
                                        // recreate DB on schema
                                        // change
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }
}

/** Repository for managing memories */
class MemoryRepository(private val database: OmniLinkDatabase) {
    private val memoryDao = database.memoryDao()
    private val messageDao = database.messageDao()

    val allMemories = memoryDao.getAllMemories()

    suspend fun remember(
            key: String,
            value: String,
            category: String = "fact",
            importance: Int = 5
    ) {
        memoryDao.upsertMemory(
                MemoryEntity(key = key, value = value, category = category, importance = importance)
        )
    }

    suspend fun recall(key: String): String? {
        val memory = memoryDao.getMemory(key)
        if (memory != null) {
            memoryDao.incrementAccessCount(key)
        }
        return memory?.value
    }

    suspend fun getContextMemories(limit: Int = 20): List<MemoryEntity> {
        memoryDao.deleteExpiredMemories()
        return memoryDao.getTopMemories(limit)
    }

    suspend fun search(query: String): List<MemoryEntity> {
        return memoryDao.searchMemories(query)
    }

    suspend fun forget(key: String) {
        memoryDao.getMemory(key)?.let { memoryDao.deleteMemory(it) }
    }

    suspend fun saveMessage(role: String, content: String, sessionId: String) {
        messageDao.insertMessage(
                MessageEntity(role = role, content = content, sessionId = sessionId)
        )
    }

    suspend fun getRecentMessages(limit: Int = 20): List<MessageEntity> {
        return messageDao.getRecentMessages(limit)
    }

    fun getSessionMessages(sessionId: String) = messageDao.getSessionMessages(sessionId)
}
