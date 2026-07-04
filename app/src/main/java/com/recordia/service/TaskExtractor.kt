package com.recordia.service

import android.util.Log
import com.recordia.data.Task
import com.recordia.data.TaskRepository
import com.recordia.network.ExtractedTask
import com.recordia.network.GeminiClient
import com.recordia.network.OpenAIClient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TaskExtractor(
    private val apiKey: String,
    private val aiProvider: String = "openai",
    private val repository: TaskRepository
) {
    private val openAIClient = OpenAIClient()
    private val geminiClient = GeminiClient()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    suspend fun processText(text: String): List<Task> {
        if (apiKey.isBlank()) {
            Log.w("TaskExtractor", "API key not configured")
            return emptyList()
        }

        val result = when (aiProvider) {
            "gemini" -> geminiClient.extractTasksFromText(text, apiKey)
            else -> openAIClient.extractTasksFromText(text, apiKey)
        }

        return result.fold(
            onSuccess = { extractedTasks ->
                Log.i("TaskExtractor", "Extracted ${extractedTasks.size} tasks: $extractedTasks")
                saveExtractedTasks(extractedTasks)
            },
            onFailure = { error ->
                Log.e("TaskExtractor", "Extraction failed: ${error.message}", error)
                emptyList()
            }
        )
    }

    suspend fun processAudioAndExtract(audioBase64: String): List<Task> {
        if (apiKey.isBlank()) {
            Log.w("TaskExtractor", "API key not configured")
            return emptyList()
        }

        val result = when (aiProvider) {
            "gemini" -> {
                Log.w("TaskExtractor", "Gemini does not support direct audio processing, transcribing first")
                val transcription = openAIClient.transcribeAudio(audioBase64, apiKey)
                transcription.fold(
                    onSuccess = { text ->
                        geminiClient.extractTasksFromText(text, apiKey)
                    },
                    onFailure = { error ->
                        Result.failure(error)
                    }
                )
            }
            else -> openAIClient.analyzeAndExtractTasks(audioBase64, apiKey)
        }

        return result.fold(
            onSuccess = { extractedTasks ->
                saveExtractedTasks(extractedTasks)
            },
            onFailure = { error ->
                Log.e("TaskExtractor", "Audio extraction failed: ${error.message}", error)
                emptyList()
            }
        )
    }

    private suspend fun saveExtractedTasks(extractedTasks: List<ExtractedTask>): List<Task> {
        val savedTasks = mutableListOf<Task>()

        for (extracted in extractedTasks) {
            if (extracted.title.isBlank()) continue

            val dueDateMillis = parseDateTime(extracted.dueDate, extracted.time)

            val task = Task(
                title = extracted.title,
                description = extracted.description,
                dueDateMillis = dueDateMillis,
                source = "ai_auto"
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
                val parts = timeStr.split(":")
                if (parts.size == 2) {
                    calendar.set(Calendar.HOUR_OF_DAY, parts[0].toIntOrNull() ?: 12)
                    calendar.set(Calendar.MINUTE, parts[1].toIntOrNull() ?: 0)
                }
            } catch (e: Exception) {
                Log.w("TaskExtractor", "Failed to parse time: $timeStr")
            }
        }

        return calendar.timeInMillis
    }
}
