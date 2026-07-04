package com.recordia.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class TaskRepository(val context: Context) {
    private val taskDao = TaskDatabase.getInstance(context).taskDao()

    fun getContext(): Context = context

    fun getAllTasks(): Flow<List<Task>> = taskDao.getAllTasks()

    fun getPendingTasks(): Flow<List<Task>> = taskDao.getPendingTasks()

    fun getCompletedTasks(): Flow<List<Task>> = taskDao.getCompletedTasks()

    suspend fun getTaskById(id: Long): Task? = taskDao.getTaskById(id)

    suspend fun insertTask(task: Task): Long = taskDao.insertTask(task)

    suspend fun updateTask(task: Task) = taskDao.updateTask(task)

    suspend fun deleteTask(task: Task) = taskDao.deleteTask(task)

    suspend fun deleteTaskById(id: Long) = taskDao.deleteTaskById(id)

    suspend fun toggleTaskCompleted(id: Long, isCompleted: Boolean) =
        taskDao.setTaskCompleted(id, isCompleted)

    suspend fun getTasksDueInRange(startMillis: Long, endMillis: Long): List<Task> =
        taskDao.getTasksDueInRange(startMillis, endMillis)

    suspend fun getOverdueTasks(threshold: Long): List<Task> =
        taskDao.getOverdueTasks(threshold)
}
