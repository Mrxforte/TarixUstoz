package com.tarixustoz.app.presentation.common

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tarixustoz.app.MainActivity
import com.tarixustoz.app.R
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class DailyNotificationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val contentRepository: ContentRepository,
    private val preferences: UserPreferencesRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!preferences.dailyReminder.first()) {
            return Result.success()
        }
        val grade = preferences.selectedGrade.first()
        val question = contentRepository.getDailyQuestion(grade = grade)
            ?: contentRepository.getRandomQuestionForNotification()
            ?: return Result.success()

        preferences.recordIncomingDailyNotification(question.text)

        val solveIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("daily_question_id", question.id)
            putExtra("open_route", "quiz/daily")
        }

        val solvePendingIntent = PendingIntent.getActivity(
            context, 1011, solveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val textbooksIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_route", "textbooks")
        }

        val textbooksPendingIntent = PendingIntent.getActivity(
            context, 1012, textbooksIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "daily_quiz_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Kun Savoli (Tarix Ustoz)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Har kuni yangi tarixiy kun savoli eslatmasi va harakat tugmalari"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationTitle = "Kun savoli • Tarix Ustoz 🎯"
        val notificationText = "${question.cleanText}\n\nJavob berib +10 Malaka qo‘lga kiriting!"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notificationTitle)
            .setContentText(question.cleanText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(solvePendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Savolni yechish 🎯",
                solvePendingIntent
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Darsliklar 📚",
                textbooksPendingIntent
            )
            .setAutoCancel(true)
            .build()

        notificationManager.notify(101, notification)

        return Result.success()
    }
}
