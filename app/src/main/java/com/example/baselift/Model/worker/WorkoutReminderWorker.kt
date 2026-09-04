package com.example.baselift.Model.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.baselift.BaseLiftApplication
import com.example.baselift.MainActivity
import com.example.baselift.R
import kotlinx.coroutines.flow.first

class WorkoutReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getInt("SESSION_ID", -1)
        if (sessionId == -1) return Result.success()

        val db = (applicationContext as BaseLiftApplication).container.database
        val dao = db.workoutDao()

        // Apenas enviamos notificação se existirem sets completados e a sessão não foi terminada
        val setsFlow = dao.getSetsForSession(sessionId)
        val sets = setsFlow.first()
        val hasCompletedSets = sets.any { it.isCompleted }

        if (hasCompletedSets) {
            sendNotification(sessionId)
        }

        return Result.success()
    }

    private fun sendNotification(sessionId: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "workout_reminder_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Workout Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders for uncompleted workouts"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("baselift://workout"))
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder) // Ícone de relógio/lembrete à esquerda
            .setColor(android.graphics.Color.parseColor("#CCFF00")) // Pinta o reloginho de verde!
            .setContentTitle("Workout in Progress")
            .setContentText("Your workout is still active. Did you forget to finish it?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(sessionId, notification)
    }
}
