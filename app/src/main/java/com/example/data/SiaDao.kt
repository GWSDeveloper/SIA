package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SiaDao {

    // Conversation messages
    @Query("SELECT * FROM conversation_messages ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversation_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ConversationEntity>

    @Query("SELECT COUNT(*) FROM conversation_messages")
    suspend fun getMessageCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationEntity): Long

    @Update
    suspend fun updateMessage(message: ConversationEntity)

    @Query("DELETE FROM conversation_messages WHERE id <= :messageId")
    suspend fun deleteMessagesUpTo(messageId: Long)

    @Query("DELETE FROM conversation_messages")
    suspend fun clearAllMessages()

    // Permanent Memory
    @Query("SELECT * FROM permanent_memories ORDER BY updatedAtMs DESC")
    fun getAllMemoriesFlow(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM permanent_memories ORDER BY updatedAtMs DESC")
    suspend fun getAllMemories(): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("DELETE FROM permanent_memories")
    suspend fun clearAllMemories()

    // Rolling Summary
    @Query("SELECT * FROM conversation_summaries WHERE id = 1 LIMIT 1")
    suspend fun getSummary(): ConversationSummaryEntity?

    @Query("SELECT * FROM conversation_summaries WHERE id = 1 LIMIT 1")
    fun getSummaryFlow(): Flow<ConversationSummaryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSummary(summary: ConversationSummaryEntity)

    // Scheduled Tasks
    @Query("SELECT * FROM scheduled_tasks ORDER BY scheduledTimeMs ASC")
    fun getAllScheduledTasksFlow(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE status = 'PENDING' ORDER BY scheduledTimeMs ASC")
    suspend fun getPendingTasks(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id LIMIT 1")
    suspend fun getScheduledTaskById(id: String): ScheduledTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledTask(task: ScheduledTaskEntity)

    @Update
    suspend fun updateScheduledTask(task: ScheduledTaskEntity)

    @Delete
    suspend fun deleteScheduledTask(task: ScheduledTaskEntity)

    @Query("UPDATE scheduled_tasks SET status = :status WHERE id = :id")
    suspend fun updateTaskStatus(id: String, status: String)
}
