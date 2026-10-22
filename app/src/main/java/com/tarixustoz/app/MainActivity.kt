package com.tarixustoz.app

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tarixustoz.app.presentation.common.DailyNotificationWorker
import com.tarixustoz.app.presentation.navigation.TarixNavHost
import com.tarixustoz.app.presentation.theme.TarixUstozTheme
import com.tarixustoz.app.presentation.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setupDailyNotifications(this)
        
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            TarixUstozTheme(darkTheme = darkTheme) {
                TarixNavHost()
            }
        }
    }
}

fun setupDailyNotifications(context: Context) {
    val workRequest = PeriodicWorkRequestBuilder<DailyNotificationWorker>(
        12, TimeUnit.HOURS
    ).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "daily_history_question",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
