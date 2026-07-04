package com.recordia.service

import android.util.Log
import com.recordia.data.Task
import com.recordia.data.TaskRepository
import com.recordia.network.ExtractedTask
import com.recordia.network.OpenAIClient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TaskExtractor(
    private val apiKey: String,
    private val repository: TaskRepository
) {
    private val client = OpenAIClient()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

    suspend fun processText(text: String): List<Task> {
        if (apiKey.isBlank()) {
            Log.w("TaskExtractor", "API key not configured")
            return emptyList()
        }

        val result = client.extractTasksFromText(text, apiKey)

        return result.fold(
            onSuccess = { extractedTasks ->
                saveExtractedTasks(extractedTasks)
            },
            onFailure = { error ->
                Log.e("TaskExtractor", "Extraction failed", error)
                emptyList()
            }
        )
    }

    suspend fun processAudioAndExtract(audioBase64: String): List<Task> {
        if (apiKey.isBlank()) {
            Log.w("TaskExtractor", "API key not configured")
            return emptyList()
        }

        val result = client.analyzeAndExtractTasks(audioBase64, apiKey)

        return result.fold(
            onSuccess = { extractedTasks ->
                saveExtractedTasks(extractedTasks)
            },
            onFailure = { error ->
                Log.e("TaskExtractor", "Audio extraction failed", error)
                emptyList()
            }
        )
    }

    private suspend fun saveExtractedTasks(extractedTasks: List<ExtractedTask>): List<Task> {
        val savedTasks = mutableListOf<Task>()

        for (extracted in extractedTasks) {
            val dueDateMillis = parseDateTime(extracted.dueDate, extracted.time)

            val task = Task(
                title = extracted.title,
                description = extracted.description,
                dueDateMillis = dueDateMillis,
                source = "voice_auto"
            )

            val id = repository.insertTask(task)
            savedTasks.add(task.copy(id = id))
        }

        return savedTasks
    }

    private fun parseDateTime(dateStr: String, timeStr: String): Long {
        val calendar = Calendar.getInstance()

        if (dateStr.isNotBlank()) {
            try {
                val date = dateFormat.parse(dateStr)
                if (date != null) {
                    calendar.time = date
                }
            } catch (e: Exception) {
                Log.w("TaskExtractor", "Failed to parse date: $dateStr")
            }
        }

        if (timeStr.isNotBlank()) {
            try {
                val timeCal = Calendar.getInstance()
                val time = timeFormat.parse(timeStr)
                if (time != null) {
                    timeCal.time = time
                    calendar.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                    calendar.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                }
            } catch (e: Exception) {
                Log.w("TaskExtractor", "Failed to parse time: $timeStr")
            }
        }

        return calendar.timeInMillis
    }
}
