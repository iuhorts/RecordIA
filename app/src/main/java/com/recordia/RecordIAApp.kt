package com.recordia

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class RecordIAApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            val listeningChannel = NotificationChannel(
                "recordia_listening",
                "Escucha Activa",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación del servicio de escucha continua"
                setSound(null, null)
            }

            val taskChannel = NotificationChannel(
                "recordia_tasks",
                "Recordatorios",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de tareas y recordatorios"
            }

            nm.createNotificationChannel(listeningChannel)
            nm.createNotificationChannel(taskChannel)
        }
    }
}
