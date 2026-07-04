package com.recordia.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.recordia.MainActivity
import com.recordia.R
import com.recordia.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ListeningService : Service() {

    companion object {
        const val CHANNEL_ID = "recordia_listening"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.recordia.STOP_LISTENING"
        const val ACTION_PAUSE = "com.recordia.PAUSE_LISTENING"
        const val ACTION_RESUME = "com.recordia.RESUME_LISTENING"

        private val _isListening = MutableStateFlow(false)
        val isListening: StateFlow<Boolean> = _isListening

        private val _isPaused = MutableStateFlow(false)
        val isPaused: StateFlow<Boolean> = _isPaused
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private lateinit var repository: TaskRepository
    private lateinit var taskExtractor: TaskExtractor
    private var apiKey: String = ""
    private var aiProvider: String = "openai"

    private var audioBuffer: ByteArrayOutputStream = ByteArrayOutputStream()
    private var silenceDuration: Long = 0
    private var isSpeaking: Boolean = false

    private val sampleRate = 16000
    private val silenceThreshold = 500
    private val maxSilenceMs = 2000L
    private val chunkDurationMs = 30000L

    override fun onCreate() {
        super.onCreate()
        repository = TaskRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListening()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                pauseListening()
                return START_STICKY
            }
            ACTION_RESUME -> {
                intent.getStringExtra("api_key")?.let { apiKey = it }
                intent.getStringExtra("ai_provider")?.let { aiProvider = it }
                resumeListening()
                return START_STICKY
            }
            else -> {
                apiKey = intent?.getStringExtra("api_key") ?: ""
                aiProvider = intent?.getStringExtra("ai_provider") ?: "openai"
                startListening()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW
        ).setName("Escucha Activa")
            .setDescription("Notificación del servicio de escucha de RecordIA")
            .build()

        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, ListeningService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RecordIA")
            .setContentText("Escuchando...")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Detener", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun startListening() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("ListeningService", "Record audio permission not granted")
            stopSelf()
            return
        }

        if (apiKey.isBlank()) {
            Log.w("ListeningService", "API key not configured")
            android.widget.Toast.makeText(
                this,
                "Configura tu API Key en Ajustes primero",
                android.widget.Toast.LENGTH_LONG
            ).show()
            stopSelf()
            return
        }

        _isListening.value = true
        _isPaused.value = false

        startForeground(NOTIFICATION_ID, buildNotification())

        taskExtractor = TaskExtractor(apiKey, aiProvider, repository)

        recordingJob = serviceScope.launch {
            startAudioCapture()
        }
    }

    private fun pauseListening() {
        _isPaused.value = true
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RecordIA")
            .setContentText("En pausa")
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setOngoing(true)
            .setSilent(true)
            .build()

        val nm = NotificationManagerCompat.from(this)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun resumeListening() {
        _isPaused.value = false
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        recordingJob = serviceScope.launch {
            startAudioCapture()
        }
    }

    private fun stopListening() {
        _isListening.value = false
        _isPaused.value = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private suspend fun startAudioCapture() {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("ListeningService", "AudioRecord failed to initialize")
                return
            }

            audioRecord?.startRecording()

            val buffer = ShortArray(bufferSize)
            var lastAudioTime = System.currentTimeMillis()
            var totalAudioBuffer = ByteArrayOutputStream()

            while (kotlin.coroutines.coroutineContext.isActive && _isListening.value && !_isPaused.value) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                if (read > 0) {
                    val byteBuffer = ByteArray(read * 2)
                    for (i in 0 until read) {
                        val high = (buffer[i].toInt() shr 8).toByte()
                        val low = (buffer[i].toInt() and 0xFF).toByte()
                        byteBuffer[i * 2] = low
                        byteBuffer[i * 2 + 1] = high
                    }

                    val rms = calculateRMS(buffer, read)
                    val currentTime = System.currentTimeMillis()

                    if (rms > silenceThreshold) {
                        totalAudioBuffer.write(byteBuffer)
                        lastAudioTime = currentTime
                        silenceDuration = 0

                        if (!isSpeaking) {
                            isSpeaking = true
                        }
                    } else {
                        if (isSpeaking) {
                            silenceDuration += currentTime - lastAudioTime
                            lastAudioTime = currentTime

                            if (silenceDuration >= maxSilenceMs) {
                                processAudioChunk(totalAudioBuffer)
                                totalAudioBuffer = ByteArrayOutputStream()
                                isSpeaking = false
                                silenceDuration = 0
                            }
                        }
                    }

                    if (totalAudioBuffer.size() > sampleRate * 2 * 30) {
                        if (isSpeaking) {
                            processAudioChunk(totalAudioBuffer)
                        }
                        totalAudioBuffer = ByteArrayOutputStream()
                        isSpeaking = false
                        silenceDuration = 0
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ListeningService", "Audio capture error", e)
        }
    }

    private suspend fun processAudioChunk(audioData: ByteArrayOutputStream) {
        if (audioData.size() < sampleRate) return

        try {
            updateNotification("Procesando audio...")

            val audioBytes = audioData.toByteArray()

            val wavFile = File(cacheDir, "temp_audio_${System.currentTimeMillis()}.wav")
            writeWavFile(wavFile, audioBytes, sampleRate)

            val fileBytes = wavFile.readBytes()
            val base64Audio = Base64.encodeToString(fileBytes, Base64.NO_WRAP)

            val tasks = taskExtractor.processAudioAndExtract(base64Audio)

            if (tasks.isNotEmpty()) {
                val taskNames = tasks.joinToString(", ") { it.title }
                Log.i("ListeningService", "Tasks created: $taskNames")

                updateNotification("Tarea(s): $taskNames")

                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@ListeningService,
                        "RecordIA: $taskNames",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                updateNotification("Escuchando...")
            }

            wavFile.delete()
        } catch (e: Exception) {
            Log.e("ListeningService", "Error processing audio", e)
            updateNotification("Error: ${e.message?.take(50)}")
            delay(2000)
            updateNotification("Escuchando...")
        }
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RecordIA")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setSilent(true)
            .build()
        val nm = NotificationManagerCompat.from(this)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun calculateRMS(buffer: ShortArray, readSize: Int): Double {
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += (buffer[i] * buffer[i]).toDouble()
        }
        return Math.sqrt(sum / readSize)
    }

    private fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int) {
        FileOutputStream(file).use { fos ->
            val dataSize = pcmData.size
            val fileSize = 36 + dataSize

            writeLEInt(fos, 0x46464952)
            writeLEInt(fos, fileSize)
            writeLEInt(fos, 0x45564157)
            writeLEInt(fos, 0x20746D66)
            writeLEInt(fos, 16)
            writeLEShort(fos, 1)
            writeLEShort(fos, 1)
            writeLEInt(fos, sampleRate)
            writeLEInt(fos, sampleRate * 2)
            writeLEShort(fos, 2)
            writeLEShort(fos, 16)
            writeLEInt(fos, 0x61746164)
            writeLEInt(fos, dataSize)
            fos.write(pcmData)
        }
    }

    private fun writeLEInt(stream: FileOutputStream, value: Int) {
        stream.write(value and 0xFF)
        stream.write((value shr 8) and 0xFF)
        stream.write((value shr 16) and 0xFF)
        stream.write((value shr 24) and 0xFF)
    }

    private fun writeLEShort(stream: FileOutputStream, value: Int) {
        stream.write(value and 0xFF)
        stream.write((value shr 8) and 0xFF)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        audioRecord?.release()
        audioRecord = null
        _isListening.value = false
        _isPaused.value = false
        super.onDestroy()
    }
}
