package com.recordia.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class GeminiRequest(
    val contents: List<Content>
)

data class Content(
    val parts: List<Part>
)

data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

data class InlineData(
    val mimeType: String,
    val data: String
)

data class GeminiResponse(
    val candidates: List<Candidate>?
)

data class Candidate(
    val content: Content?
)

class GeminiClient {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun transcribeAndExtract(
        audioBase64: String,
        apiKey: String
    ): Result<List<ExtractedTask>> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
Eres un asistente que extrae tareas de conversaciones en español.
Analiza el audio transcrito y extrae cualquier tarea mencionada.
Responde SOLO con un JSON array de objetos con campos: title, description, dueDate, time
Si no hay tareas, responde con un array vacío [].
            """.trimIndent()

            val requestBody = GeminiRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(inlineData = InlineData(mimeType = "audio/wav", data = audioBase64)),
                            Part(text = prompt)
                        )
                    )
                )
            )

            val jsonBody = gson.toJson(requestBody)
            val mediaType = "application/json; charset=utf-8".toMediaType()

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                .post(jsonBody.toRequestBody(mediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                Log.e("GeminiClient", "API error: $responseBody")
                return@withContext Result.failure(Exception("API error: ${response.code}"))
            }

            val geminiResponse = gson.fromJson(responseBody, GeminiResponse::class.java)
            val content = geminiResponse.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text ?: ""

            val cleaned = content.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val tasks = gson.fromJson(cleaned, Array<ExtractedTask>::class.java)
                ?.toList() ?: emptyList()

            Result.success(tasks)
        } catch (e: Exception) {
            Log.e("GeminiClient", "Error transcribing with Gemini", e)
            Result.failure(e)
        }
    }

    suspend fun extractTasksFromText(
        text: String,
        apiKey: String
    ): Result<List<ExtractedTask>> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
Eres un asistente que extrae tareas de conversaciones en español.
Analiza el texto y extrae cualquier tarea mencionada, identificando:
- Título de la tarea
- Descripción breve
- Fecha de vencimiento (en formato YYYY-MM-DD, o "" si no se menciona)
- Hora (en formato HH:MM, o "" si no se menciona)

Si no se menciona una fecha explícita pero el contexto implica un plazo (ej: "esta semana", "mañana", "el lunes"), calcula la fecha relativa al día actual.

Responde SOLO con un JSON array de objetos con campos: title, description, dueDate, time
Cada objeto representa una tarea extraída.
Si no hay tareas, responde con un array vacío [].

Texto a analizar:
$text
            """.trimIndent()

            val requestBody = GeminiRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(text = "Eres un extractor de tareas. Responde solo con JSON."),
                            Part(text = prompt)
                        )
                    )
                )
            )

            val jsonBody = gson.toJson(requestBody)
            val mediaType = "application/json; charset=utf-8".toMediaType()

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
                .post(jsonBody.toRequestBody(mediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                Log.e("GeminiClient", "API error: $responseBody")
                return@withContext Result.failure(Exception("API error: ${response.code}"))
            }

            val geminiResponse = gson.fromJson(responseBody, GeminiResponse::class.java)
            val content = geminiResponse.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text ?: ""

            val cleaned = content.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val tasks = gson.fromJson(cleaned, Array<ExtractedTask>::class.java)
                ?.toList() ?: emptyList()

            Result.success(tasks)
        } catch (e: Exception) {
            Log.e("GeminiClient", "Error extracting tasks", e)
            Result.failure(e)
        }
    }
}
