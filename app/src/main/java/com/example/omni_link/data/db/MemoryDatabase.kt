package com.example.omni_link.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Memory entity - stores persistent knowledge for the AI
 * This is for Track 1: Memory Master
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val category: String, // preference, fact, context, task
    val importance: Int = 5, // 1-10 scale
    val accessCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null // null = never expires
)

/**
 * Conversation message entity - stores chat history
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String, // USER, ASSISTANT, SYSTEM
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String // Groups messages by session
)

/**
 * Data Access Object for memories
 */
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

    @Query("SELECT * FROM memories WHERE key LIKE '%' || :query || '%' OR value LIKE '%' || :query || '%'")
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(memory: MemoryEntity)

    @Query("UPDATE memories SET accessCount = accessCount + 1, updatedAt = :now WHERE key = :key")
    suspend fun incrementAccessCount(key: String, now: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE expiresAt IS NOT NULL AND expiresAt < :now")
    suspend fun deleteExpiredMemories(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM memories")
    suspend fun clearAllMemories()
}

/**
 * Data Access Object for messages
 */
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getSessionMessages(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<MessageEntity>

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()
}

/**
 * Room database for persistent storage
 */
@Database(
    entities = [MemoryEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OmniLinkDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: OmniLinkDatabase? = null

        fun getInstance(context: Context): OmniLinkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OmniLinkDatabase::class.java,
                    "omnilink_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Repository for managing memories
 */
class MemoryRepository(private val database: OmniLinkDatabase) {
    private val memoryDao = database.memoryDao()
    private val messageDao = database.messageDao()

    val allMemories = memoryDao.getAllMemories()

    suspend fun remember(key: String, value: String, category: String = "fact", importance: Int = 5) {
        memoryDao.upsertMemory(
            MemoryEntity(
                key = key,
                value = value,
                category = category,
                importance = importance
            )
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
            MessageEntity(
                role = role,
                content = content,
                sessionId = sessionId
            )
        )
    }

    suspend fun getRecentMessages(limit: Int = 20): List<MessageEntity> {
        return messageDao.getRecentMessages(limit)
    }

    fun getSessionMessages(sessionId: String) = messageDao.getSessionMessages(sessionId)
}








