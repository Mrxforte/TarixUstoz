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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Best score and passed state of every ticket of one category. */
data class TicketProgressSnapshot(
    val scores: Map<Int, Int> = emptyMap(),
    val passed: Set<Int> = emptySet()
)

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
        private val USER_PHONE = stringPreferencesKey("user_phone")
        private val USER_TARGET = stringPreferencesKey("user_target")
        private val FAVORITE_QUESTIONS = stringSetPreferencesKey("favorite_questions")
        private val CAMPAIGN_GRADE = intPreferencesKey("campaign_grade")
        private val CAMPAIGN_CHAPTER = intPreferencesKey("campaign_chapter")
        private val NOTIF_DAILY_REMINDER = booleanPreferencesKey("notif_daily_reminder")
        private val NOTIF_MISTAKE_REMINDER = booleanPreferencesKey("notif_mistake_reminder")
        private val NOTIF_NEW_TESTS = booleanPreferencesKey("notif_new_tests")
        private val NOTIF_SOUND = booleanPreferencesKey("notif_sound")
        private val NOTIF_TIME = stringPreferencesKey("notif_time")
        private val ACTIVE_QUIZ_ROUTE = stringPreferencesKey("active_quiz_route")
        private val ACTIVE_QUIZ_TITLE = stringPreferencesKey("active_quiz_title")
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

    val userPhone: Flow<String> = dataStore.data.map { it[USER_PHONE] ?: "+998 90 123 45 67" }

    val userTarget: Flow<String> = dataStore.data.map { it[USER_TARGET] ?: "DTM 189 ball • O‘zbekiston tarixi" }

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

    suspend fun setUserPhone(phone: String) {
        dataStore.edit { it[USER_PHONE] = phone }
    }

    suspend fun setUserTarget(target: String) {
        dataStore.edit { it[USER_TARGET] = target }
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

    suspend fun saveTicketResult(categoryKey: String, ticketIndex: Int, score: Int, passed: Boolean) {
        dataStore.edit { prefs ->
            val scoreKey = intPreferencesKey("ticket_score_${categoryKey}_$ticketIndex")
            val passedKey = booleanPreferencesKey("ticket_passed_${categoryKey}_$ticketIndex")
            val currentScore = prefs[scoreKey] ?: 0
            if (score > currentScore) {
                prefs[scoreKey] = score
            }
            if (passed) {
                prefs[passedKey] = true
            }
        }
    }

    fun getTicketScore(categoryKey: String, ticketIndex: Int): Flow<Int> {
        val scoreKey = intPreferencesKey("ticket_score_${categoryKey}_$ticketIndex")
        return dataStore.data.map { it[scoreKey] ?: 0 }
    }

    fun isTicketPassed(categoryKey: String, ticketIndex: Int): Flow<Boolean> {
        val passedKey = booleanPreferencesKey("ticket_passed_${categoryKey}_$ticketIndex")
        return dataStore.data.map { it[passedKey] ?: false }
    }

    fun isTicketUnlocked(categoryKey: String, ticketIndex: Int): Flow<Boolean> {
        if (ticketIndex <= 1) return flowOf(true)
        val prevPassedKey = booleanPreferencesKey("ticket_passed_${categoryKey}_${ticketIndex - 1}")
        return dataStore.data.map { it[prevPassedKey] ?: false }
    }

    /** One snapshot of every ticket of a category — thousands of per-key lookups are too slow. */
    suspend fun getTicketProgress(categoryKey: String): TicketProgressSnapshot {
        val prefs = dataStore.data.first()
        val scorePrefix = "ticket_score_${categoryKey}_"
        val passedPrefix = "ticket_passed_${categoryKey}_"
        val scores = mutableMapOf<Int, Int>()
        val passed = mutableSetOf<Int>()
        prefs.asMap().forEach { (key, value) ->
            val name = key.name
            when {
                name.startsWith(scorePrefix) ->
                    name.removePrefix(scorePrefix).toIntOrNull()?.let { scores[it] = (value as? Int) ?: 0 }
                name.startsWith(passedPrefix) && value == true ->
                    name.removePrefix(passedPrefix).toIntOrNull()?.let { passed += it }
            }
        }
        return TicketProgressSnapshot(scores, passed)
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

    val activeQuizRoute: Flow<String?> = dataStore.data.map { it[ACTIVE_QUIZ_ROUTE] }
    val activeQuizTitle: Flow<String?> = dataStore.data.map { it[ACTIVE_QUIZ_TITLE] }

    suspend fun setActiveQuiz(route: String, title: String) {
        dataStore.edit {
            it[ACTIVE_QUIZ_ROUTE] = route
            it[ACTIVE_QUIZ_TITLE] = title
        }
    }

    suspend fun clearActiveQuiz() {
        dataStore.edit {
            it.remove(ACTIVE_QUIZ_ROUTE)
            it.remove(ACTIVE_QUIZ_TITLE)
        }
    }
}
