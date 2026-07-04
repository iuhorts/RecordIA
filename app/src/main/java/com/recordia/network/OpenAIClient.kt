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

data class ExtractedTask(
    val title: String,
    val description: String = "",
    val dueDate: String = "",
    val time: String = ""
)

data class OpenAIRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<Message>,
    @SerializedName("max_tokens")
    val maxTokens: Int = 300
)

data class Message(
    val role: String,
    val content: String
)

data class OpenAIResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)

class OpenAIClient {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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

            val requestBody = OpenAIRequest(
                messages = listOf(
                    Message("system", "Eres un extractor de tareas. Responde solo con JSON."),
                    Message("user", prompt)
                ),
                maxTokens = 500
            )

            val jsonBody = gson.toJson(requestBody)
            val mediaType = "application/json; charset=utf-8".toMediaType()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toRequestBody(mediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                Log.e("OpenAIClient", "API error: $responseBody")
                return@withContext Result.failure(Exception("API error: ${response.code}"))
            }

            val openAIResponse = gson.fromJson(responseBody, OpenAIResponse::class.java)
            val content = openAIResponse.choices.firstOrNull()?.message?.content ?: ""

            val cleaned = content.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val tasks = gson.fromJson(cleaned, Array<ExtractedTask>::class.java)
                ?.toList() ?: emptyList()

            Result.success(tasks)
        } catch (e: Exception) {
            Log.e("OpenAIClient", "Error extracting tasks", e)
            Result.failure(e)
        }
    }

    suspend fun transcribeAudio(
        audioBase64: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
Eres un transcriptor de audio a texto en español.
Transcribe el audio exactamente como se escucha.
            """.trimIndent()

            val requestBody = OpenAIRequest(
                model = "gpt-4o-audio-preview",
                messages = listOf(
                    Message("user", """
[Audio base64]
$audioBase64
[/Audio]
Transcribe este audio al español.
                    """.trimIndent())
                ),
                maxTokens = 500
            )

            val jsonBody = gson.toJson(requestBody)
            val mediaType = "application/json; charset=utf-8".toMediaType()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toRequestBody(mediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("API error: ${response.code}"))
            }

            val openAIResponse = gson.fromJson(responseBody, OpenAIResponse::class.java)
            val transcription = openAIResponse.choices.firstOrNull()?.message?.content ?: ""

            Result.success(transcription.trim())
        } catch (e: Exception) {
            Log.e("OpenAIClient", "Error transcribing audio", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeAndExtractTasks(
        audioBase64: String,
        apiKey: String
    ): Result<List<ExtractedTask>> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = """
Eres RecordIA, un asistente secreto que siempre está escuchando.
Del audio proporcionado, extrae cualquier tarea que la persona haya mencionado.

Para cada tarea identifica:
- title: nombre corto de la tarea
- description: descripción o contexto
- dueDate: fecha de vencimiento en formato YYYY-MM-DD (calcula fechas relativas)
- time: hora en formato HH:MM

Responde SOLO con un JSON array. Si no hay tareas, responde [].
            """.trimIndent()

            val requestBody = OpenAIRequest(
                model = "gpt-4o-audio-preview",
                messages = listOf(
                    Message("system", systemPrompt),
                    Message("user", "Procesa este audio y extrae las tareas mencionadas:\n[Audio base64]\n$audioBase64\n[/Audio]")
                ),
                maxTokens = 500
            )

            val jsonBody = gson.toJson(requestBody)
            val mediaType = "application/json; charset=utf-8".toMediaType()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toRequestBody(mediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("API error: ${response.code}"))
            }

            val openAIResponse = gson.fromJson(responseBody, OpenAIResponse::class.java)
            val content = openAIResponse.choices.firstOrNull()?.message?.content ?: ""

            val cleaned = content.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val tasks = gson.fromJson(cleaned, Array<ExtractedTask>::class.java)
                ?.toList() ?: emptyList()

            Result.success(tasks)
        } catch (e: Exception) {
            Log.e("OpenAIClient", "Error in full pipeline", e)
            Result.failure(e)
        }
    }
}
