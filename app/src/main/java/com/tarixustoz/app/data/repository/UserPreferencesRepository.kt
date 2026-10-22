package com.tarixustoz.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
        private val USER_BIO = stringPreferencesKey("user_bio")
        private val USER_AVATAR = stringPreferencesKey("user_avatar")
        private val FAVORITE_QUESTIONS = stringSetPreferencesKey("favorite_questions")
        private val CAMPAIGN_GRADE = intPreferencesKey("campaign_grade")
        private val CAMPAIGN_CHAPTER = intPreferencesKey("campaign_chapter")
        private val NOTIF_DAILY_REMINDER = booleanPreferencesKey("notif_daily_reminder")
        private val NOTIF_MISTAKE_REMINDER = booleanPreferencesKey("notif_mistake_reminder")
        private val NOTIF_NEW_TESTS = booleanPreferencesKey("notif_new_tests")
        private val NOTIF_SOUND = booleanPreferencesKey("notif_sound")
        private val NOTIF_TIME = stringPreferencesKey("notif_time")
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        when (prefs[THEME_MODE]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val selectedGrade: Flow<Int> = dataStore.data.map { it[SELECTED_GRADE] ?: 7 }

    // Modest, realistic small XP number (e.g. 78 XP, Level 2)
    val userXp: Flow<Long> = dataStore.data.map { it[USER_XP] ?: 78L }

    val userStreak: Flow<Int> = dataStore.data.map { it[USER_STREAK] ?: 7 }

    val displayName: Flow<String> = dataStore.data.map { it[DISPLAY_NAME] ?: "Azizbek" }

    val userBio: Flow<String> = dataStore.data.map { it[USER_BIO] ?: "Tarix bilimdoni • DTM tayyorgarlik" }

    val userAvatar: Flow<String> = dataStore.data.map { it[USER_AVATAR] ?: "AS" }

    val favorites: Flow<Set<String>> = dataStore.data.map { it[FAVORITE_QUESTIONS] ?: emptySet() }

    val campaignGrade: Flow<Int> = dataStore.data.map { it[CAMPAIGN_GRADE] ?: 6 }
    val campaignChapter: Flow<Int> = dataStore.data.map { it[CAMPAIGN_CHAPTER] ?: 1 }

    val dailyReminder: Flow<Boolean> = dataStore.data.map { it[NOTIF_DAILY_REMINDER] ?: true }
    val mistakeReminder: Flow<Boolean> = dataStore.data.map { it[NOTIF_MISTAKE_REMINDER] ?: true }
    val newTestsReminder: Flow<Boolean> = dataStore.data.map { it[NOTIF_NEW_TESTS] ?: true }
    val soundEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIF_SOUND] ?: true }
    val reminderTime: Flow<String> = dataStore.data.map { it[NOTIF_TIME] ?: "08:30" }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setSelectedGrade(grade: Int) {
        dataStore.edit { it[SELECTED_GRADE] = grade }
    }

    suspend fun addXp(amount: Long) {
        dataStore.edit {
            val current = it[USER_XP] ?: 78L
            it[USER_XP] = current + amount
        }
    }

    suspend fun setStreak(streak: Int) {
        dataStore.edit { it[USER_STREAK] = streak }
    }

    suspend fun setDisplayName(name: String) {
        dataStore.edit { it[DISPLAY_NAME] = name }
    }

    suspend fun setUserBio(bio: String) {
        dataStore.edit { it[USER_BIO] = bio }
    }

    suspend fun setUserAvatar(avatar: String) {
        dataStore.edit { it[USER_AVATAR] = avatar }
    }

    suspend fun setDailyReminder(enabled: Boolean) {
        dataStore.edit { it[NOTIF_DAILY_REMINDER] = enabled }
    }

    suspend fun setMistakeReminder(enabled: Boolean) {
        dataStore.edit { it[NOTIF_MISTAKE_REMINDER] = enabled }
    }

    suspend fun setNewTestsReminder(enabled: Boolean) {
        dataStore.edit { it[NOTIF_NEW_TESTS] = enabled }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIF_SOUND] = enabled }
    }

    suspend fun setReminderTime(time: String) {
        dataStore.edit { it[NOTIF_TIME] = time }
    }

    suspend fun toggleFavorite(questionId: String): Boolean {
        var isNowFav = false
        dataStore.edit { prefs ->
            val current = prefs[FAVORITE_QUESTIONS] ?: emptySet()
            if (current.contains(questionId)) {
                prefs[FAVORITE_QUESTIONS] = current - questionId
                isNowFav = false
            } else {
                prefs[FAVORITE_QUESTIONS] = current + questionId
                isNowFav = true
            }
        }
        return isNowFav
    }

    suspend fun advanceCampaign() {
        dataStore.edit { prefs ->
            val curChapter = prefs[CAMPAIGN_CHAPTER] ?: 1
            val curGrade = prefs[CAMPAIGN_GRADE] ?: 6
            if (curChapter >= 5) {
                // Move to next grade book
                if (curGrade < 11) {
                    prefs[CAMPAIGN_GRADE] = curGrade + 1
                    prefs[CAMPAIGN_CHAPTER] = 1
                }
            } else {
                prefs[CAMPAIGN_CHAPTER] = curChapter + 1
            }
        }
    }
}
