package com.tarixustoz.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tarixustoz.app.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val SELECTED_GRADE = intPreferencesKey("selected_grade")
        private val USER_XP = longPreferencesKey("user_xp")
        private val USER_STREAK = intPreferencesKey("user_streak")
        private val DISPLAY_NAME = stringPreferencesKey("display_name")
        private val LAST_STUDY_DATE = stringPreferencesKey("last_study_date")
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        when (prefs[THEME_MODE]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val selectedGrade: Flow<Int> = dataStore.data.map { it[SELECTED_GRADE] ?: 7 }

    val userXp: Flow<Long> = dataStore.data.map { it[USER_XP] ?: 0L }

    val userStreak: Flow<Int> = dataStore.data.map { it[USER_STREAK] ?: 0 }

    val displayName: Flow<String> = dataStore.data.map { it[DISPLAY_NAME] ?: "O‘quvchi" }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setSelectedGrade(grade: Int) {
        dataStore.edit { it[SELECTED_GRADE] = grade }
    }

    suspend fun addXp(amount: Long) {
        dataStore.edit {
            val current = it[USER_XP] ?: 0L
            it[USER_XP] = current + amount
        }
    }

    suspend fun setStreak(streak: Int) {
        dataStore.edit { it[USER_STREAK] = streak }
    }

    suspend fun setDisplayName(name: String) {
        dataStore.edit { it[DISPLAY_NAME] = name }
    }
}
