package com.recordia.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.recordia.MainActivity
import com.recordia.R
import com.recordia.data.Task
import com.recordia.data.TaskRepository
import kotlinx.coroutines.runBlocking

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("task_id", -1)
        val taskTitle = intent.getStringExtra("task_title") ?: "Tarea pendiente"
        val taskDesc = intent.getStringExtra("task_desc") ?: ""

        val channelId = "recordia_tasks"

        val channel = NotificationChannelCompat.Builder(
            channelId,
            NotificationManagerCompat.IMPORTANCE_HIGH
        ).setName("Recordatorios")
            .setDescription("Notificaciones de tareas pendientes")
            .build()

        NotificationManagerCompat.from(context).createNotificationChannel(channel)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context, taskId.toInt(), openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📋 $taskTitle")
            .setContentText(taskDesc.ifBlank { "Tienes una tarea pendiente" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(taskId.toInt(), notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val repository = TaskRepository(context)
            val tasks = runBlocking {
                repository.getOverdueTasks(System.currentTimeMillis())
            }
            for (task in tasks) {
                if (!task.isCompleted) {
                    scheduleTaskReminder(context, task)
                }
            }
        }
    }
}

fun scheduleTaskReminder(context: Context, task: Task) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val intent = Intent(context, TaskReminderReceiver::class.java).apply {
        putExtra("task_id", task.id)
        putExtra("task_title", task.title)
        putExtra("task_desc", task.description)
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        task.id.toInt(),
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, task.dueDateMillis, pendingIntent
                )
            } else {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP, task.dueDateMillis, 60000, pendingIntent
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, task.dueDateMillis, pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP, task.dueDateMillis, pendingIntent
            )
        }
    } catch (e: Exception) {
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP, task.dueDateMillis, 60000, pendingIntent
        )
    }
}

fun cancelTaskReminder(context: Context, taskId: Long) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val intent = Intent(context, TaskReminderReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        taskId.toInt(),
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
    )

    pendingIntent?.let {
        alarmManager.cancel(it)
        it.cancel()
    }
}
