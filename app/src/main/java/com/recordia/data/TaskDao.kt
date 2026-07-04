package com.recordia.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY dueDateMillis ASC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY dueDateMillis ASC")
    fun getPendingTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 1 ORDER BY dueDateMillis DESC")
    fun getCompletedTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("UPDATE tasks SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun setTaskCompleted(id: Long, isCompleted: Boolean)

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND dueDateMillis BETWEEN :startMillis AND :endMillis")
    suspend fun getTasksDueInRange(startMillis: Long, endMillis: Long): List<Task>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND dueDateMillis <= :threshold")
    suspend fun getOverdueTasks(threshold: Long): List<Task>
}
