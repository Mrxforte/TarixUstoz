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
import com.tarixustoz.app.domain.model.Question
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ActiveQuizState(
    val quizRoute: String,
    val quizTitle: String,
    val questions: List<Question>,
    val currentIndex: Int,
    val answeredCorrectly: List<Boolean?>,
    val correctCount: Int
)

/** Best score, passed state, and last opened ticket of every ticket of one category. */
data class TicketProgressSnapshot(
    val scores: Map<Int, Int> = emptyMap(),
    val passed: Set<Int> = emptySet(),
    val lastOpenedTicket: Int = 1
)

@Serializable
data class PendingQuizAttempt(
    val attemptId: String,
    val categoryKey: String,
    val categoryTitle: String,
    val grade: Int,
    val solvedCount: Int,
    val correctCount: Int,
    val durationSeconds: Int,
    val createdAtMs: Long = System.currentTimeMillis()
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val ACTIVE_USER_UID = stringPreferencesKey("active_user_uid")
        private val SELECTED_GRADE = intPreferencesKey("selected_grade")
        private val USER_XP = longPreferencesKey("user_xp")
        private val USER_STREAK = intPreferencesKey("user_streak")
        private val LAST_ACTIVE_DAY = stringPreferencesKey("last_active_day")
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
        private val ACTIVE_QUIZ_JSON = stringPreferencesKey("active_quiz_json")
        private val REWARDED_AD_DATE = stringPreferencesKey("rewarded_ad_date")
        private val REWARDED_AD_COUNT = intPreferencesKey("rewarded_ad_count")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val IS_GUEST = booleanPreferencesKey("is_guest")
        private val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
        private val TOTAL_SOLVED_TESTS = intPreferencesKey("total_solved_tests")
        private val TOTAL_CORRECT_ANSWERS = intPreferencesKey("total_correct_answers")
        private val TOTAL_TIME_SPENT_SECONDS = intPreferencesKey("total_time_spent_seconds")
        private val USER_BIRTH_YEAR = stringPreferencesKey("user_birth_year")
        private val USER_ROLE = stringPreferencesKey("user_role")
        private val READ_NOTIFICATION_IDS = stringSetPreferencesKey("read_notification_ids")
        private val DISMISSED_NOTIFICATION_IDS = stringSetPreferencesKey("dismissed_notification_ids")
        private val LAST_DAILY_NOTIFICATION_TEXT = stringPreferencesKey("last_daily_notification_text")
        private val IS_TEST_MODE_ACTIVE = booleanPreferencesKey("is_test_mode_active")
        private val LAST_OFFLINE_SYNC_AT_MS = longPreferencesKey("last_offline_sync_at_ms")
        private val PENDING_SYNC_CATEGORIES = stringSetPreferencesKey("pending_sync_categories")
        private val PENDING_SYNC_ATTEMPTS_JSON = stringPreferencesKey("pending_sync_attempts_json")
        private val PENDING_STATS_SYNC = booleanPreferencesKey("pending_stats_sync")
    }

    val activeUserUid: Flow<String?> = dataStore.data.map { it[ACTIVE_USER_UID] }

    suspend fun setActiveUserUid(uid: String?) {
        dataStore.edit { prefs ->
            if (uid.isNullOrBlank()) prefs.remove(ACTIVE_USER_UID)
            else prefs[ACTIVE_USER_UID] = uid
        }
    }

    val userBirthYear: Flow<String> = dataStore.data.map { it[USER_BIRTH_YEAR] ?: "" }
    val userRole: Flow<String> = dataStore.data.map { it[USER_ROLE] ?: "O'quvchi" }

    suspend fun setUserBirthYear(year: String) {
        dataStore.edit { it[USER_BIRTH_YEAR] = year }
    }

    suspend fun setUserRole(role: String) {
        dataStore.edit { it[USER_ROLE] = role }
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

    val displayName: Flow<String> = dataStore.data.map { it[DISPLAY_NAME] ?: "Tarixchi" }

    val userBio: Flow<String> = dataStore.data.map { it[USER_BIO] ?: "Tarix fani izlanuvchisi" }

    val userAvatar: Flow<String> = dataStore.data.map { it[USER_AVATAR] ?: "TU" }

    val userPhone: Flow<String> = dataStore.data.map { it[USER_PHONE] ?: "" }

    val userTarget: Flow<String> = dataStore.data.map { it[USER_TARGET] ?: "DTM va Milliy sertifikat" }

    val favorites: Flow<Set<String>> = dataStore.data.map { it[FAVORITE_QUESTIONS] ?: emptySet() }

    val campaignGrade: Flow<Int> = dataStore.data.map { it[CAMPAIGN_GRADE] ?: 6 }
    val campaignChapter: Flow<Int> = dataStore.data.map { it[CAMPAIGN_CHAPTER] ?: 1 }

    val dailyReminder: Flow<Boolean> = dataStore.data.map { it[NOTIF_DAILY_REMINDER] ?: true }
    val mistakeReminder: Flow<Boolean> = dataStore.data.map { it[NOTIF_MISTAKE_REMINDER] ?: true }
    val newTestsReminder: Flow<Boolean> = dataStore.data.map { it[NOTIF_NEW_TESTS] ?: true }
    val soundEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIF_SOUND] ?: true }
    val reminderTime: Flow<String> = dataStore.data.map { it[NOTIF_TIME] ?: "08:30" }
    val readNotificationIds: Flow<Set<String>> = dataStore.data.map { it[READ_NOTIFICATION_IDS] ?: emptySet() }
    val dismissedNotificationIds: Flow<Set<String>> = dataStore.data.map { it[DISMISSED_NOTIFICATION_IDS] ?: emptySet() }
    val lastDailyNotificationText: Flow<String?> = dataStore.data.map { it[LAST_DAILY_NOTIFICATION_TEXT] }

    val totalSolvedTests: Flow<Int> = dataStore.data.map { it[TOTAL_SOLVED_TESTS] ?: 0 }
    val totalCorrectAnswers: Flow<Int> = dataStore.data.map { it[TOTAL_CORRECT_ANSWERS] ?: 0 }
    val totalTimeSpentSeconds: Flow<Int> = dataStore.data.map { it[TOTAL_TIME_SPENT_SECONDS] ?: 0 }

    suspend fun recordQuizCompletion(
        solvedCount: Int,
        correctCount: Int = 0,
        timeSpentSeconds: Int
    ) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(Date(System.currentTimeMillis() - 86_400_000L))
        dataStore.edit { prefs ->
            val currentSolved = prefs[TOTAL_SOLVED_TESTS] ?: 0
            val currentCorrect = prefs[TOTAL_CORRECT_ANSWERS] ?: 0
            val currentTime = prefs[TOTAL_TIME_SPENT_SECONDS] ?: 0
            prefs[TOTAL_SOLVED_TESTS] = (currentSolved + solvedCount).coerceAtLeast(0)
            prefs[TOTAL_CORRECT_ANSWERS] = (currentCorrect + correctCount).coerceAtLeast(0)
            prefs[TOTAL_TIME_SPENT_SECONDS] = (currentTime + timeSpentSeconds).coerceAtLeast(0)

            val lastDay = prefs[LAST_ACTIVE_DAY]
            val currentStreak = prefs[USER_STREAK] ?: 0
            when (lastDay) {
                today -> if (currentStreak == 0) prefs[USER_STREAK] = 1
                yesterday -> prefs[USER_STREAK] = currentStreak + 1
                else -> prefs[USER_STREAK] = 1
            }
            prefs[LAST_ACTIVE_DAY] = today
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setSelectedGrade(grade: Int) {
        dataStore.edit { it[SELECTED_GRADE] = grade }
    }

    suspend fun addXp(amount: Long) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(Date(System.currentTimeMillis() - 86_400_000L))
        dataStore.edit { prefs ->
            val current = prefs[USER_XP] ?: 0L
            prefs[USER_XP] = (current + amount).coerceAtLeast(0L)
            val lastDay = prefs[LAST_ACTIVE_DAY]
            val currentStreak = prefs[USER_STREAK] ?: 0
            if (lastDay != today) {
                prefs[USER_STREAK] = if (lastDay == yesterday) currentStreak + 1 else 1
                prefs[LAST_ACTIVE_DAY] = today
            }
        }
    }

    suspend fun setStreak(streak: Int) {
        dataStore.edit { it[USER_STREAK] = streak.coerceAtLeast(0) }
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

    suspend fun markNotificationRead(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[READ_NOTIFICATION_IDS] ?: emptySet()
            prefs[READ_NOTIFICATION_IDS] = current + id
        }
    }

    suspend fun markAllNotificationsRead(ids: Collection<String>) {
        dataStore.edit { prefs ->
            val current = prefs[READ_NOTIFICATION_IDS] ?: emptySet()
            prefs[READ_NOTIFICATION_IDS] = current + ids
        }
    }

    suspend fun dismissNotification(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[DISMISSED_NOTIFICATION_IDS] ?: emptySet()
            prefs[DISMISSED_NOTIFICATION_IDS] = current + id
        }
    }

    suspend fun restoreNotification(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[DISMISSED_NOTIFICATION_IDS] ?: emptySet()
            prefs[DISMISSED_NOTIFICATION_IDS] = current - id
        }
    }

    suspend fun restoreAllNotifications() {
        dataStore.edit { prefs ->
            prefs[DISMISSED_NOTIFICATION_IDS] = emptySet()
        }
    }

    suspend fun recordIncomingDailyNotification(questionText: String) {
        dataStore.edit { prefs ->
            prefs[LAST_DAILY_NOTIFICATION_TEXT] = questionText
            prefs[READ_NOTIFICATION_IDS] = (prefs[READ_NOTIFICATION_IDS] ?: emptySet()) - "n_daily_quiz"
            prefs[DISMISSED_NOTIFICATION_IDS] = (prefs[DISMISSED_NOTIFICATION_IDS] ?: emptySet()) - "n_daily_quiz"
        }
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

    suspend fun setLastOpenedTicket(categoryKey: String, ticketIndex: Int) {
        val openedKey = intPreferencesKey("last_opened_ticket_$categoryKey")
        dataStore.edit { prefs ->
            prefs[openedKey] = ticketIndex.coerceAtLeast(1)
        }
    }

    suspend fun getLastOpenedTicket(categoryKey: String): Int {
        val openedKey = intPreferencesKey("last_opened_ticket_$categoryKey")
        return dataStore.data.first()[openedKey] ?: 1
    }

    suspend fun saveTicketResult(categoryKey: String, ticketIndex: Int, score: Int, passed: Boolean) {
        dataStore.edit { prefs ->
            val scoreKey = intPreferencesKey("ticket_score_${categoryKey}_$ticketIndex")
            val passedKey = booleanPreferencesKey("ticket_passed_${categoryKey}_$ticketIndex")
            val openedKey = intPreferencesKey("last_opened_ticket_$categoryKey")
            val currentScore = prefs[scoreKey] ?: 0
            if (score > currentScore) {
                prefs[scoreKey] = score
            }
            if (passed) {
                prefs[passedKey] = true
            }
            prefs[openedKey] = ticketIndex.coerceAtLeast(1)
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
        return getTicketProgressFromPrefs(prefs, categoryKey)
    }

    fun getTicketProgressFromPrefs(prefs: Preferences, categoryKey: String): TicketProgressSnapshot {
        val scorePrefix = "ticket_score_${categoryKey}_"
        val passedPrefix = "ticket_passed_${categoryKey}_"
        val openedKey = intPreferencesKey("last_opened_ticket_$categoryKey")
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
        val lastOpened = prefs[openedKey] ?: (passed.maxOrNull()?.plus(1) ?: 1)
        return TicketProgressSnapshot(scores, passed, lastOpened.coerceAtLeast(1))
    }

    suspend fun mergeTicketProgressFromCloud(
        categoryKey: String,
        passedTickets: Set<Int>,
        scores: Map<Int, Int>,
        lastOpenedTicket: Int
    ) {
        dataStore.edit { prefs ->
            scores.forEach { (idx, cloudScore) ->
                if (idx >= 1) {
                    val scoreKey = intPreferencesKey("ticket_score_${categoryKey}_$idx")
                    val existing = prefs[scoreKey] ?: 0
                    if (cloudScore > existing) {
                        prefs[scoreKey] = cloudScore
                    }
                    if (cloudScore >= 10) {
                        prefs[booleanPreferencesKey("ticket_passed_${categoryKey}_$idx")] = true
                    }
                }
            }
            passedTickets.forEach { idx ->
                if (idx >= 1) {
                    prefs[booleanPreferencesKey("ticket_passed_${categoryKey}_$idx")] = true
                }
            }
            val openedKey = intPreferencesKey("last_opened_ticket_$categoryKey")
            val currentOpened = prefs[openedKey] ?: 1
            if (lastOpenedTicket > currentOpened) {
                prefs[openedKey] = lastOpenedTicket
            }
        }
    }

    suspend fun applyCloudUserProfile(
        displayName: String,
        email: String,
        bio: String,
        avatar: String,
        phone: String,
        target: String,
        birthYear: String,
        role: String,
        grade: Int,
        xp: Long,
        streak: Int,
        totalSolved: Int,
        correctCount: Int,
        timeSpentSeconds: Int
    ) {
        dataStore.edit { prefs ->
            prefs[DISPLAY_NAME] = displayName.ifBlank { "Tarixchi" }
            if (email.isNotBlank()) prefs[USER_EMAIL] = email else prefs.remove(USER_EMAIL)
            prefs[USER_BIO] = bio.ifBlank { "Tarix fani izlanuvchisi" }
            prefs[USER_AVATAR] = avatar.ifBlank { "TU" }
            prefs[USER_PHONE] = phone
            prefs[USER_TARGET] = target.ifBlank { "DTM va Milliy sertifikat" }
            prefs[USER_BIRTH_YEAR] = birthYear
            prefs[USER_ROLE] = role.ifBlank { "O'quvchi" }
            prefs[SELECTED_GRADE] = grade.coerceIn(6, 11)
            prefs[USER_XP] = xp.coerceAtLeast(0L)
            prefs[USER_STREAK] = streak.coerceAtLeast(0)
            prefs[TOTAL_SOLVED_TESTS] = totalSolved.coerceAtLeast(0)
            prefs[TOTAL_CORRECT_ANSWERS] = correctCount.coerceAtLeast(0)
            prefs[TOTAL_TIME_SPENT_SECONDS] = timeSpentSeconds.coerceAtLeast(0)
            prefs[IS_GUEST] = false
            prefs[IS_TEST_MODE_ACTIVE] = false
        }
    }

    suspend fun clearUserSessionDataForSwitch() {
        dataStore.edit { prefs ->
            val preservedKeys = setOf(
                THEME_MODE.name,
                IS_ONBOARDING_COMPLETED.name,
                NOTIF_DAILY_REMINDER.name,
                NOTIF_MISTAKE_REMINDER.name,
                NOTIF_NEW_TESTS.name,
                NOTIF_SOUND.name,
                NOTIF_TIME.name,
                LAST_OFFLINE_SYNC_AT_MS.name
            )
            val keysToRemove = prefs.asMap().keys.filter { it.name !in preservedKeys }
            keysToRemove.forEach { prefs.remove(it) }
            prefs[IS_GUEST] = true
            prefs[IS_TEST_MODE_ACTIVE] = false
        }
    }

    suspend fun getAllPreferences(): Preferences {
        return dataStore.data.first()
    }

    suspend fun advanceCampaign() {
        dataStore.edit { prefs ->
            val curChapter = prefs[CAMPAIGN_CHAPTER] ?: 1
            val curGrade = prefs[CAMPAIGN_GRADE] ?: 6
            if (curChapter >= 5) {
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
    val activeQuizState: Flow<ActiveQuizState?> = dataStore.data.map { prefs ->
        val jsonStr = prefs[ACTIVE_QUIZ_JSON]
        if (jsonStr.isNullOrBlank()) null
        else {
            try {
                Json.decodeFromString<ActiveQuizState>(jsonStr)
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun saveActiveQuizState(state: ActiveQuizState) {
        val jsonStr = Json.encodeToString(state)
        dataStore.edit {
            it[ACTIVE_QUIZ_JSON] = jsonStr
            it[ACTIVE_QUIZ_ROUTE] = state.quizRoute
            it[ACTIVE_QUIZ_TITLE] = state.quizTitle
        }
    }

    suspend fun clearActiveQuiz() {
        dataStore.edit {
            it.remove(ACTIVE_QUIZ_JSON)
            it.remove(ACTIVE_QUIZ_ROUTE)
            it.remove(ACTIVE_QUIZ_TITLE)
        }
    }

    val rewardedAdCountToday: Flow<Int> = dataStore.data.map { prefs ->
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val savedDate = prefs[REWARDED_AD_DATE] ?: ""
        if (savedDate == today) prefs[REWARDED_AD_COUNT] ?: 0 else 0
    }

    val canShowRewardedAdToday: Flow<Boolean> = rewardedAdCountToday.map { it < 2 }

    suspend fun incrementRewardedAdCount(): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        var allowed = false
        dataStore.edit { prefs ->
            val savedDate = prefs[REWARDED_AD_DATE] ?: ""
            val currentCount = if (savedDate == today) prefs[REWARDED_AD_COUNT] ?: 0 else 0
            if (currentCount < 2) {
                prefs[REWARDED_AD_DATE] = today
                prefs[REWARDED_AD_COUNT] = currentCount + 1
                allowed = true
            } else {
                allowed = false
            }
        }
        return allowed
    }

    val userEmail: Flow<String?> = dataStore.data.map { it[USER_EMAIL] }
    val isGuest: Flow<Boolean> = dataStore.data.map { it[IS_GUEST] ?: true }
    val isTestModeActive: Flow<Boolean> = dataStore.data.map { it[IS_TEST_MODE_ACTIVE] ?: false }
    val lastOfflineSyncAtMs: Flow<Long> = dataStore.data.map { it[LAST_OFFLINE_SYNC_AT_MS] ?: 0L }

    val pendingSyncCategories: Flow<Set<String>> = dataStore.data.map {
        it[PENDING_SYNC_CATEGORIES] ?: emptySet()
    }
    val pendingStatsSync: Flow<Boolean> = dataStore.data.map { it[PENDING_STATS_SYNC] ?: false }

    private val syncJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    val pendingQuizAttempts: Flow<List<PendingQuizAttempt>> = dataStore.data.map { prefs ->
        decodePendingAttempts(prefs[PENDING_SYNC_ATTEMPTS_JSON])
    }

    val pendingSyncCount: Flow<Int> = dataStore.data.map { prefs ->
        val attemptsCount = decodePendingAttempts(prefs[PENDING_SYNC_ATTEMPTS_JSON]).size
        val catsCount = (prefs[PENDING_SYNC_CATEGORIES] ?: emptySet()).size
        val statsPending = if (prefs[PENDING_STATS_SYNC] == true) 1 else 0
        attemptsCount + catsCount + statsPending
    }

    private fun decodePendingAttempts(raw: String?): List<PendingQuizAttempt> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            syncJson.decodeFromString<List<PendingQuizAttempt>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    val isOnboardingCompleted: Flow<Boolean> = dataStore.data.map { it[IS_ONBOARDING_COMPLETED] ?: false }

    suspend fun setUserEmail(email: String?) {
        dataStore.edit {
            if (email != null) it[USER_EMAIL] = email else it.remove(USER_EMAIL)
        }
    }

    suspend fun setIsGuest(isGuest: Boolean) {
        dataStore.edit { it[IS_GUEST] = isGuest }
    }

    suspend fun setTestModeActive(active: Boolean) {
        dataStore.edit {
            it[IS_TEST_MODE_ACTIVE] = active
            if (active) it[IS_GUEST] = true
        }
    }

    suspend fun setLastOfflineSyncAtMs(timestampMs: Long) {
        dataStore.edit { it[LAST_OFFLINE_SYNC_AT_MS] = timestampMs }
    }

    suspend fun markCategoryPendingSync(categoryKey: String) {
        dataStore.edit { prefs ->
            val current = prefs[PENDING_SYNC_CATEGORIES] ?: emptySet()
            prefs[PENDING_SYNC_CATEGORIES] = current + categoryKey
        }
    }

    suspend fun clearCategoryPendingSync(categoryKey: String) {
        dataStore.edit { prefs ->
            val current = prefs[PENDING_SYNC_CATEGORIES] ?: emptySet()
            if (categoryKey in current) {
                prefs[PENDING_SYNC_CATEGORIES] = current - categoryKey
            }
        }
    }

    suspend fun setPendingStatsSync(pending: Boolean) {
        dataStore.edit { it[PENDING_STATS_SYNC] = pending }
    }

    suspend fun enqueuePendingQuizAttempt(attempt: PendingQuizAttempt) {
        dataStore.edit { prefs ->
            val list = decodePendingAttempts(prefs[PENDING_SYNC_ATTEMPTS_JSON]).toMutableList()
            if (list.none { it.attemptId == attempt.attemptId }) {
                list.add(attempt)
            }
            val bounded = if (list.size > 100) list.takeLast(100) else list
            prefs[PENDING_SYNC_ATTEMPTS_JSON] = syncJson.encodeToString(bounded)
        }
    }

    suspend fun removePendingQuizAttempt(attemptId: String) {
        dataStore.edit { prefs ->
            val list = decodePendingAttempts(prefs[PENDING_SYNC_ATTEMPTS_JSON])
                .filterNot { it.attemptId == attemptId }
            if (list.isEmpty()) {
                prefs.remove(PENDING_SYNC_ATTEMPTS_JSON)
            } else {
                prefs[PENDING_SYNC_ATTEMPTS_JSON] = syncJson.encodeToString(list)
            }
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean = true) {
        dataStore.edit { it[IS_ONBOARDING_COMPLETED] = completed }
    }
}
