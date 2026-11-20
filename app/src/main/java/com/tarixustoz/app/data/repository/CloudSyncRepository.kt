package com.tarixustoz.app.data.repository

import android.util.Log
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.data.remote.OperationType
import com.tarixustoz.app.data.remote.handleFirestoreError
import com.tarixustoz.app.data.remote.isFirestoreOfflineOrTransient
import com.tarixustoz.app.data.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Keep
@IgnoreExtraProperties
data class CloudUserProfile(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val displayName: String = "Tarixchi",
    val email: String = "",
    val bio: String = "",
    val avatar: String = "TU",
    val phone: String = "",
    val target: String = "",
    val birthYear: String = "",
    val role: String = "O'quvchi",
    val grade: Long = 7L,
    val xp: Long = 0L,
    val streak: Long = 0L,
    val totalSolved: Long = 0L,
    val correctCount: Long = 0L,
    val timeSpentSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudTicketProgress(
    @DocumentId val docId: String = "",
    val categoryKey: String = "",
    val passedTickets: List<Long> = emptyList(),
    val scores: Map<String, Long> = emptyMap(),
    val lastOpenedTicket: Long = 1L,
    val highestUnlockedTicket: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudQuizAttempt(
    @DocumentId val docId: String = "",
    val categoryKey: String = "",
    val categoryTitle: String = "",
    val grade: Long = 0L,
    val solvedCount: Long = 0L,
    val correctCount: Long = 0L,
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudLeaderboardEntry(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val initials: String = "TU",
    val xp: Long = 0L,
    val streak: Long = 0L,
    val totalSolved: Long = 0L,
    val correctCount: Long = 0L,
    val accuracy: Long = 0L,
    val timeSpentMinutes: Long = 0L,
    val grade: Long = 7L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Singleton
class CloudSyncRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val preferences: UserPreferencesRepository,
    private val localDb: AppDatabase,
    private val syncScheduler: SyncScheduler
) {

    companion object {
        private const val TAG = "CloudSyncRepository"
        private val VALID_ID_REGEX = Regex("[^a-zA-Z0-9_\\-]")

        fun sanitizeDocId(raw: String): String {
            val cleaned = raw.trim().replace(VALID_ID_REGEX, "_")
            return if (cleaned.isBlank()) "default" else cleaned.take(128)
        }
    }

    private fun computeInitials(name: String, avatar: String): String {
        val trimmedAvatar = avatar.trim()
        if (trimmedAvatar.isNotEmpty() && trimmedAvatar.length <= 6) {
            return trimmedAvatar.take(6)
        }
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val init = when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "TU"
        }
        return init.ifBlank { "TU" }.take(8)
    }

    // In-memory daily cache for user attempts so observeUserAttempts reads Firestore at most once per day
    private val dailyAttemptsMemoryCache = mutableMapOf<String, List<CloudQuizAttempt>>()
    private val statsPushMutex = Mutex()

    private fun todayDateKey(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    private suspend fun DocumentReference.getCacheFirstSnapshot(): DocumentSnapshot? {
        val cached = runCatching { get(Source.CACHE).await() }.getOrNull()
        if (cached != null && cached.exists()) return cached
        return try {
            withTimeoutOrNull(4500L) {
                get(Source.DEFAULT).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isFirestoreOfflineOrTransient()) {
                cached
            } else {
                throw e
            }
        }
    }

    /**
     * Ensures signed-in user has their Firestore profile & leaderboard entry,
     * and hydrates local DataStore preferences & ticket progress from Firestore
     * at most ONCE per day per user.
     */
    suspend fun syncOnSignIn(user: FirebaseUser): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = user.uid
        try {
            val previousUid = preferences.activeUserUid.first()
            val isSameUser = previousUid == uid
            if (previousUid != null && !isSameUser) {
                preferences.clearUserSessionDataForSwitch()
                localDb.quizAttemptDao().deleteAll()
                localDb.wrongQuestionDao().deleteAll()
                localDb.userProgressDao().deleteAll()
            }
            preferences.setActiveUserUid(uid)

            // Kuniga faqat 1 marta Firebase'dan o'qish (agar shu foydalanuvchi bugun o'qilgan bo'lsa, qayta o'qimaymiz)
            if (isSameUser && preferences.hasReadFromFirebaseToday(uid)) {
                // Agar bugun hali yozilmagan bo'lsa va kutilayotgan lokal natijalar bo'lsa, kunlik 1 marta yozishni bajaramiz
                if (!preferences.hasWrittenToFirebaseToday(uid)) {
                    flushPendingUploads()
                }
                return@withContext Result.success(Unit)
            }

            val userRef = db.collection("users").document(uid)
            val snapshot = try {
                withTimeoutOrNull(5000L) {
                    userRef.get(Source.DEFAULT).await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isFirestoreOfflineOrTransient()) null else throw e
            } ?: runCatching { userRef.get(Source.CACHE).await() }.getOrNull()
                ?: return@withContext Result.failure(IllegalStateException("Firestore offline during sign-in sync"))

            if (snapshot.exists()) {
                val cloud = snapshot.toObject(
                    CloudUserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (cloud != null) {
                    val authName = user.displayName?.takeIf { it.isNotBlank() }
                    val emailPrefix = user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    val cloudName = cloud.displayName.takeIf { it.isNotBlank() && it != "Azizbek" && it != "Tarixchi" }

                    val safeName = authName 
                        ?: emailPrefix 
                        ?: cloudName 
                        ?: "Tarixchi"
                    preferences.applyCloudUserProfile(
                        displayName = safeName,
                        email = cloud.email.ifBlank { user.email ?: "" },
                        bio = cloud.bio,
                        avatar = cloud.avatar.ifBlank { computeInitials(safeName, "") },
                        phone = cloud.phone,
                        target = cloud.target,
                        birthYear = cloud.birthYear,
                        role = cloud.role.ifBlank { "O'quvchi" },
                        grade = cloud.grade.toInt().coerceIn(6, 11),
                        xp = cloud.xp.coerceAtLeast(0L),
                        streak = cloud.streak.toInt().coerceAtLeast(0),
                        totalSolved = cloud.totalSolved.toInt().coerceAtLeast(0),
                        correctCount = cloud.correctCount.toInt().coerceAtLeast(0),
                        timeSpentSeconds = cloud.timeSpentSeconds.toInt().coerceAtLeast(0)
                    )
                }
                pullAllTicketProgressFromCloud(uid)
                preferences.markFirebaseReadToday(uid)
                ensureUserLeaderboardFields(snapshot)
                ensureLeaderboardSubdoc(uid)
            } else {
                val defaultName = (user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.email?.substringBefore("@")
                    ?: "Tarixchi").take(60)
                val initials = computeInitials(defaultName, "")
                val email = (user.email ?: "").take(160)
                val role = preferences.userRole.first().trim().ifBlank { "O'quvchi" }.take(40)
                val grade = preferences.selectedGrade.first().coerceIn(6, 11)
                val xp = preferences.userXp.first().coerceAtLeast(0L)
                val streak = preferences.userStreak.first().coerceAtLeast(0)
                val solved = localDb.quizAttemptDao().getTotalCount().coerceAtLeast(0)
                val correct = localDb.quizAttemptDao().getCorrectCount().coerceIn(0, solved)
                val timeSec = preferences.totalTimeSpentSeconds.first().coerceAtLeast(0)

                preferences.applyCloudUserProfile(
                    displayName = defaultName,
                    email = email,
                    bio = "Tarix fani izlanuvchisi",
                    avatar = initials,
                    phone = "",
                    target = "DTM va Milliy sertifikat",
                    birthYear = "",
                    role = role,
                    grade = grade,
                    xp = xp,
                    streak = streak,
                    totalSolved = solved,
                    correctCount = correct,
                    timeSpentSeconds = timeSec
                )

                val profileMap = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", defaultName)
                    if (email.isNotBlank()) put("email", email)
                    put("bio", "Tarix fani izlanuvchisi")
                    put("avatar", initials)
                    put("target", "DTM va Milliy sertifikat")
                    put("role", role)
                    put("grade", grade)
                    put("xp", xp)
                    put("streak", streak)
                    put("totalSolved", solved)
                    put("correctCount", correct)
                    put("timeSpentSeconds", timeSec)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                userRef.set(profileMap).await()
                upsertUserLeaderboardEntry(
                    uid = uid,
                    displayName = defaultName,
                    initials = initials,
                    xp = xp,
                    streak = streak,
                    totalSolved = solved,
                    correctCount = correct,
                    timeSpentSeconds = timeSec,
                    grade = grade
                )
                preferences.markFirebaseReadToday(uid)
                preferences.markFirebaseWrittenToday(uid)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid", auth)
            Result.failure(e)
        }
    }

    suspend fun syncEditedProfileToCloud(): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.success(Unit)
        try {
            val name = preferences.displayName.first().trim().ifBlank { "Tarixchi" }.take(60)

            auth.currentUser?.let { currentUser ->
                runCatching {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()
                    currentUser.updateProfile(profileUpdates).await()
                }
            }

            val bio = preferences.userBio.first().trim().take(200)
            val phone = preferences.userPhone.first().trim().take(40)
            val target = preferences.userTarget.first().trim().take(160)
            val birthYear = preferences.userBirthYear.first().trim().take(12)
            val role = preferences.userRole.first().trim().ifBlank { "O'quvchi" }.take(40)
            val avatar = preferences.userAvatar.first().trim().ifBlank { "TU" }.take(16)
            val grade = preferences.selectedGrade.first().coerceIn(6, 11)
            val email = (auth.currentUser?.email ?: preferences.userEmail.first() ?: "").take(160)
            val xp = preferences.userXp.first().coerceAtLeast(0L)
            val streak = preferences.userStreak.first().coerceAtLeast(0)
            val solved = localDb.quizAttemptDao().getTotalCount().coerceAtLeast(0)
            val correct = localDb.quizAttemptDao().getCorrectCount().coerceIn(0, solved)
            val timeSec = preferences.totalTimeSpentSeconds.first().coerceAtLeast(0)

            val docRef = db.collection("users").document(uid)
            val snap = docRef.getCacheFirstSnapshot()
            if (snap == null) {
                preferences.setPendingStatsSync(true)
                syncScheduler.triggerPendingProgressUpload()
                return@withContext Result.failure(IllegalStateException("Firestore offline; profile update deferred"))
            }
            val writeOk = if (snap.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "displayName" to name,
                    "bio" to bio,
                    "avatar" to avatar,
                    "phone" to phone,
                    "target" to target,
                    "birthYear" to birthYear,
                    "role" to role,
                    "grade" to grade,
                    "xp" to xp,
                    "streak" to streak,
                    "totalSolved" to solved,
                    "correctCount" to correct,
                    "timeSpentSeconds" to timeSec,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (email.isNotBlank()) updates["email"] = email
                withTimeoutOrNull(5000L) {
                    docRef.update(updates).await()
                    true
                } ?: false
            } else {
                val createMap = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", name)
                    if (email.isNotBlank()) put("email", email)
                    if (bio.isNotBlank()) put("bio", bio)
                    put("avatar", avatar)
                    if (phone.isNotBlank()) put("phone", phone)
                    if (target.isNotBlank()) put("target", target)
                    if (birthYear.isNotBlank()) put("birthYear", birthYear)
                    put("role", role)
                    put("grade", grade)
                    put("xp", xp)
                    put("streak", streak)
                    put("totalSolved", solved)
                    put("correctCount", correct)
                    put("timeSpentSeconds", timeSec)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                withTimeoutOrNull(5000L) {
                    docRef.set(createMap).await()
                    true
                } ?: false
            }
            if (!writeOk) {
                preferences.setPendingStatsSync(true)
                syncScheduler.triggerPendingProgressUpload()
                return@withContext Result.failure(IllegalStateException("Firestore write deferred (offline)"))
            }
            upsertUserLeaderboardEntry(
                uid = uid,
                displayName = name,
                initials = computeInitials(name, avatar),
                xp = xp,
                streak = streak,
                totalSolved = solved,
                correctCount = correct,
                timeSpentSeconds = timeSec,
                grade = grade
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isFirestoreOfflineOrTransient()) {
                preferences.setPendingStatsSync(true)
                syncScheduler.triggerPendingProgressUpload()
            }
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid", auth)
            Result.failure(e)
        }
    }

    /**
     * Foydalanuvchi ballari va ishlagan testlarini Firebase'dagi users hujjatiga yozadi.
     * Agar qurilma oflayn bo'lsa, navbatga qo'yib qo'yadi va xatolik spami chiqarmaydi.
     */
    suspend fun pushStatsToCloud(forceImmediate: Boolean = false): Result<Unit> =
        pushStatsToCloudInternal(forceImmediate = forceImmediate, scheduleWorkerOnFailure = true)

    private suspend fun pushStatsToCloudInternal(
        forceImmediate: Boolean,
        scheduleWorkerOnFailure: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.success(Unit)
        statsPushMutex.withLock {
            preferences.setPendingStatsSync(true)
            try {
                val name = preferences.displayName.first().trim().ifBlank { "Tarixchi" }.take(60)
                val avatar = preferences.userAvatar.first().trim().ifBlank { "TU" }.take(16)
                val grade = preferences.selectedGrade.first().coerceIn(6, 11)
                val xp = preferences.userXp.first().coerceAtLeast(0L)
                val streak = preferences.userStreak.first().coerceAtLeast(0)
                val localSolved = localDb.quizAttemptDao().getTotalCount()
                val prefSolved = preferences.totalSolvedTests.first()
                val totalSolved = maxOf(localSolved, prefSolved).coerceAtLeast(0)
                val localCorrect = localDb.quizAttemptDao().getCorrectCount()
                val prefCorrect = preferences.totalCorrectAnswers.first()
                val correctCount = maxOf(localCorrect, prefCorrect).coerceIn(0, totalSolved)
                val timeSec = preferences.totalTimeSpentSeconds.first().coerceAtLeast(0)

                val docRef = db.collection("users").document(uid)
                val snap = docRef.getCacheFirstSnapshot()
                if (snap == null) {
                    preferences.setPendingStatsSync(true)
                    if (scheduleWorkerOnFailure) {
                        syncScheduler.triggerPendingProgressUpload()
                    }
                    Log.w(TAG, "Firestore offline/timeout checking users/$uid; stats sync deferred")
                    return@withLock Result.failure(IllegalStateException("Firestore offline; stats sync deferred"))
                }

                val exists = snap.exists()
                val userMap = if (exists) {
                    mapOf(
                        "displayName" to name,
                        "avatar" to avatar,
                        "grade" to grade,
                        "xp" to xp,
                        "streak" to streak,
                        "totalSolved" to totalSolved,
                        "correctCount" to correctCount,
                        "timeSpentSeconds" to timeSec,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                } else {
                    buildMap<String, Any> {
                        put("userId", uid)
                        put("displayName", name)
                        val email = (auth.currentUser?.email ?: "").take(160)
                        if (email.isNotBlank()) put("email", email)
                        put("bio", "Tarix fani izlanuvchisi")
                        put("avatar", avatar)
                        put("target", "DTM va Milliy sertifikat")
                        put("role", "O'quvchi")
                        put("grade", grade)
                        put("xp", xp)
                        put("streak", streak)
                        put("totalSolved", totalSolved)
                        put("correctCount", correctCount)
                        put("timeSpentSeconds", timeSec)
                        put("createdAt", FieldValue.serverTimestamp())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                }
                val committed = withTimeoutOrNull(5000L) {
                    docRef.set(userMap, SetOptions.merge()).await()
                    true
                } ?: false
                if (!committed) {
                    preferences.setPendingStatsSync(true)
                    if (scheduleWorkerOnFailure) {
                        syncScheduler.triggerPendingProgressUpload()
                    }
                    Log.w(TAG, "Firestore stats write queued offline for users/$uid")
                    return@withLock Result.failure(IllegalStateException("Firestore offline; stats queued"))
                }
                upsertUserLeaderboardEntry(
                    uid = uid,
                    displayName = name,
                    initials = computeInitials(name, avatar),
                    xp = xp,
                    streak = streak,
                    totalSolved = totalSolved,
                    correctCount = correctCount,
                    timeSpentSeconds = timeSec,
                    grade = grade
                )
                preferences.setPendingStatsSync(false)
                preferences.markFirebaseWrittenToday(uid)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                preferences.setPendingStatsSync(true)
                if (scheduleWorkerOnFailure) {
                    syncScheduler.triggerPendingProgressUpload()
                }
                handleFirestoreError(e, OperationType.UPDATE, "users/$uid", auth)
                Result.failure(e)
            }
        }
    }

    private suspend fun upsertUserLeaderboardEntry(
        uid: String,
        displayName: String,
        initials: String,
        xp: Long,
        streak: Int,
        totalSolved: Int,
        correctCount: Int,
        timeSpentSeconds: Int,
        grade: Int
    ) {
        val lbRef = db.collection("users").document(uid).collection("leaderboard").document(uid)
        try {
            val safeName = displayName.trim().ifBlank { "Tarixchi" }.take(60)
            val safeInitials = initials.trim().ifBlank { computeInitials(safeName, "") }.take(8)
            val safeSolved = totalSolved.coerceIn(0, 10_000_000)
            val safeCorrect = correctCount.coerceIn(0, safeSolved)
            val accuracy = if (safeSolved > 0) ((safeCorrect * 100) / safeSolved).coerceIn(0, 100) else 0
            val minutes = (timeSpentSeconds / 60).coerceIn(0, 10_000_000)
            val safeGrade = grade.coerceIn(6, 11)
            val safeXp = xp.coerceIn(0L, 100_000_000L)
            val safeStreak = streak.coerceIn(0, 10_000)

            val snap = lbRef.getCacheFirstSnapshot() ?: return
            withTimeoutOrNull(5000L) {
                if (snap.exists()) {
                    lbRef.update(
                        mapOf(
                            "displayName" to safeName,
                            "initials" to safeInitials,
                            "xp" to safeXp,
                            "streak" to safeStreak,
                            "totalSolved" to safeSolved,
                            "correctCount" to safeCorrect,
                            "accuracy" to accuracy,
                            "timeSpentMinutes" to minutes,
                            "grade" to safeGrade,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    lbRef.set(
                        mapOf(
                            "userId" to uid,
                            "displayName" to safeName,
                            "initials" to safeInitials,
                            "xp" to safeXp,
                            "streak" to safeStreak,
                            "totalSolved" to safeSolved,
                            "correctCount" to safeCorrect,
                            "accuracy" to accuracy,
                            "timeSpentMinutes" to minutes,
                            "grade" to safeGrade,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/leaderboard/$uid", auth)
        }
    }

    private suspend fun ensureLeaderboardSubdoc(uid: String) {
        val lbRef = db.collection("users").document(uid).collection("leaderboard").document(uid)
        val snap = runCatching { lbRef.getCacheFirstSnapshot() }.getOrNull()
        if (snap == null || !snap.exists()) {
            val name = preferences.displayName.first().trim().ifBlank { "Tarixchi" }.take(60)
            val avatar = preferences.userAvatar.first().trim().ifBlank { "TU" }.take(16)
            val grade = preferences.selectedGrade.first().coerceIn(6, 11)
            val xp = preferences.userXp.first().coerceAtLeast(0L)
            val streak = preferences.userStreak.first().coerceAtLeast(0)
            val solved = maxOf(localDb.quizAttemptDao().getTotalCount(), preferences.totalSolvedTests.first()).coerceAtLeast(0)
            val correct = maxOf(localDb.quizAttemptDao().getCorrectCount(), preferences.totalCorrectAnswers.first()).coerceIn(0, solved)
            val timeSec = preferences.totalTimeSpentSeconds.first().coerceAtLeast(0)
            upsertUserLeaderboardEntry(
                uid = uid,
                displayName = name,
                initials = computeInitials(name, avatar),
                xp = xp,
                streak = streak,
                totalSolved = solved,
                correctCount = correct,
                timeSpentSeconds = timeSec,
                grade = grade
            )
        }
    }

    private suspend fun ensureUserLeaderboardFields(snapshot: DocumentSnapshot) {
        val data = snapshot.data ?: return
        val hasRequiredStats = data.containsKey("xp") &&
            data.containsKey("totalSolved") &&
            data.containsKey("correctCount") &&
            data.containsKey("timeSpentSeconds")
        if (!hasRequiredStats) {
            pushStatsToCloud(forceImmediate = true)
        }
    }

    suspend fun saveTicketProgressToCloud(
        categoryKey: String,
        passedTickets: Set<Int>,
        scores: Map<Int, Int>,
        lastOpenedTicket: Int,
        highestUnlockedTicket: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.success(Unit)
        preferences.markCategoryPendingSync(categoryKey)
        val docKey = sanitizeDocId(categoryKey)
        val docRef = db.collection("users").document(uid).collection("ticketProgress").document(docKey)
        try {
            val boundedPassed = passedTickets.sorted().take(4000)
            val boundedScores = scores.entries
                .sortedBy { it.key }
                .take(4000)
                .associate { it.key.toString() to it.value }
            val safeOpened = lastOpenedTicket.coerceIn(1, 10000)
            val safeUnlocked = highestUnlockedTicket.coerceIn(1, 10000)

            val snap = docRef.getCacheFirstSnapshot()
            if (snap == null) {
                preferences.markCategoryPendingSync(categoryKey)
                syncScheduler.triggerPendingProgressUpload()
                return@withContext Result.failure(IllegalStateException("Firestore offline; ticket progress deferred"))
            }
            val committed = withTimeoutOrNull(5000L) {
                if (snap.exists()) {
                    docRef.update(
                        mapOf(
                            "passedTickets" to boundedPassed,
                            "scores" to boundedScores,
                            "lastOpenedTicket" to safeOpened,
                            "highestUnlockedTicket" to safeUnlocked,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    docRef.set(
                        mapOf(
                            "categoryKey" to docKey,
                            "passedTickets" to boundedPassed,
                            "scores" to boundedScores,
                            "lastOpenedTicket" to safeOpened,
                            "highestUnlockedTicket" to safeUnlocked,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                true
            } ?: false
            if (!committed) {
                preferences.markCategoryPendingSync(categoryKey)
                syncScheduler.triggerPendingProgressUpload()
                return@withContext Result.failure(IllegalStateException("Firestore offline; ticket progress queued"))
            }
            preferences.clearCategoryPendingSync(categoryKey)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            preferences.markCategoryPendingSync(categoryKey)
            syncScheduler.triggerPendingProgressUpload()
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/ticketProgress/$docKey", auth)
            Result.failure(e)
        }
    }

    suspend fun pullTicketProgressForCategory(categoryKey: String): CloudTicketProgress? = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext null
        // Kuniga 1 marta Firebase'dan o'qilgan bo'lsa, qayta so'rov yubormaymiz
        if (preferences.hasReadFromFirebaseToday(uid)) {
            return@withContext null
        }
        val docKey = sanitizeDocId(categoryKey)
        try {
            val snap = db.collection("users").document(uid)
                .collection("ticketProgress").document(docKey)
                .get()
                .await()
            if (!snap.exists()) return@withContext null
            val cloud = snap.toObject(
                CloudTicketProgress::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            if (cloud != null) {
                preferences.mergeTicketProgressFromCloud(
                    categoryKey = categoryKey,
                    passedTickets = cloud.passedTickets.map { it.toInt() }.toSet(),
                    scores = cloud.scores.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v.toInt() } }.toMap(),
                    lastOpenedTicket = cloud.lastOpenedTicket.toInt().coerceAtLeast(1)
                )
            }
            cloud
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$uid/ticketProgress/$docKey", auth)
            null
        }
    }

    private suspend fun pullAllTicketProgressFromCloud(uid: String) {
        try {
            val snaps = db.collection("users").document(uid)
                .collection("ticketProgress")
                .get()
                .await()
            for (doc in snaps.documents) {
                val item = doc.toObject(
                    CloudTicketProgress::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: continue
                val key = item.categoryKey.ifBlank { doc.id }
                preferences.mergeTicketProgressFromCloud(
                    categoryKey = key,
                    passedTickets = item.passedTickets.map { it.toInt() }.toSet(),
                    scores = item.scores.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v.toInt() } }.toMap(),
                    lastOpenedTicket = item.lastOpenedTicket.toInt().coerceAtLeast(1)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$uid/ticketProgress", auth)
        }
    }

    suspend fun recordQuizAttemptToCloud(
        categoryKey: String,
        categoryTitle: String,
        grade: Int,
        solvedCount: Int,
        correctCount: Int,
        durationSeconds: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.success(Unit)
        val attemptId = "att_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val safeSolved = solvedCount.coerceIn(0, 500)
        val safeCorrect = correctCount.coerceIn(0, safeSolved)
        val pendingAttempt = PendingQuizAttempt(
            attemptId = attemptId,
            categoryKey = categoryKey,
            categoryTitle = categoryTitle,
            grade = grade,
            solvedCount = safeSolved,
            correctCount = safeCorrect,
            durationSeconds = durationSeconds
        )
        preferences.enqueuePendingQuizAttempt(pendingAttempt)

        val flushed = flushPendingUploads()
        if (flushed) Result.success(Unit) else Result.failure(IllegalStateException("Upload deferred"))
    }

    /**
     * Flushes any quiz attempts, ticket progress, or stat updates queued locally.
     */
    suspend fun flushPendingUploads(): Boolean = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext true
        var allSucceeded = true
        var wroteAny = false

        val pendingAttempts = preferences.pendingQuizAttempts.first()
        for (attempt in pendingAttempts) {
            try {
                val committed = withTimeoutOrNull(5000L) {
                    db.collection("users").document(uid)
                        .collection("attempts").document(attempt.attemptId)
                        .set(
                            mapOf(
                                "categoryKey" to sanitizeDocId(attempt.categoryKey),
                                "categoryTitle" to attempt.categoryTitle.ifBlank { "Test" }.take(120),
                                "grade" to attempt.grade.coerceIn(0, 11),
                                "solvedCount" to attempt.solvedCount.coerceIn(0, 500),
                                "correctCount" to attempt.correctCount.coerceIn(0, attempt.solvedCount.coerceAtLeast(0)),
                                "durationSeconds" to attempt.durationSeconds.coerceIn(0, 86400),
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    true
                } ?: false
                if (committed) {
                    preferences.removePendingQuizAttempt(attempt.attemptId)
                    wroteAny = true
                } else {
                    allSucceeded = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                allSucceeded = false
                handleFirestoreError(e, OperationType.CREATE, "users/$uid/attempts/${attempt.attemptId}", auth)
            }
        }

        val pendingCategories = preferences.pendingSyncCategories.first()
        for (categoryKey in pendingCategories) {
            val snap = preferences.getTicketProgress(categoryKey)
            val highestUnlocked = ((snap.passed.maxOrNull() ?: 0) + 1).coerceAtLeast(1)
            val docKey = sanitizeDocId(categoryKey)
            val docRef = db.collection("users").document(uid).collection("ticketProgress").document(docKey)
            try {
                val boundedPassed = snap.passed.sorted().take(4000)
                val boundedScores = snap.scores.entries
                    .sortedBy { it.key }
                    .take(4000)
                    .associate { it.key.toString() to it.value }
                val safeOpened = snap.lastOpenedTicket.coerceIn(1, 10000)
                val safeUnlocked = highestUnlocked.coerceIn(1, 10000)
                val existing = docRef.getCacheFirstSnapshot()
                if (existing == null) {
                    allSucceeded = false
                    continue
                }
                val committed = withTimeoutOrNull(5000L) {
                    if (existing.exists()) {
                        docRef.update(
                            mapOf(
                                "passedTickets" to boundedPassed,
                                "scores" to boundedScores,
                                "lastOpenedTicket" to safeOpened,
                                "highestUnlockedTicket" to safeUnlocked,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    } else {
                        docRef.set(
                            mapOf(
                                "categoryKey" to docKey,
                                "passedTickets" to boundedPassed,
                                "scores" to boundedScores,
                                "lastOpenedTicket" to safeOpened,
                                "highestUnlockedTicket" to safeUnlocked,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                    true
                } ?: false
                if (committed) {
                    preferences.clearCategoryPendingSync(categoryKey)
                    wroteAny = true
                } else {
                    allSucceeded = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                allSucceeded = false
                handleFirestoreError(e, OperationType.WRITE, "users/$uid/ticketProgress/$docKey", auth)
            }
        }

        if (preferences.pendingStatsSync.first() || wroteAny) {
            val res = pushStatsToCloudInternal(forceImmediate = true, scheduleWorkerOnFailure = false)
            if (res.isFailure) allSucceeded = false
            else wroteAny = true
        }

        if (allSucceeded && wroteAny) {
            preferences.markFirebaseWrittenToday(uid)
        }

        allSucceeded
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeLeaderboard(limit: Long = 500L): Flow<List<CloudLeaderboardEntry>> =
        db.collectionGroup("leaderboard")
            .snapshots()
            .flatMapLatest { querySnap ->
                val entries = mapLeaderboardDocuments(querySnap.documents, limit)
                if (entries.isEmpty()) {
                    observeUsersFallbackLeaderboard(limit)
                } else {
                    flowOf(entries)
                }
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                val root = fsError ?: error
                handleFirestoreError(root, OperationType.LIST, "leaderboard", auth)
                Log.w(TAG, "CollectionGroup leaderboard unavailable, falling back to users", root)
                emitAll(observeUsersFallbackLeaderboard(limit))
            }

    private fun observeUsersFallbackLeaderboard(limit: Long): Flow<List<CloudLeaderboardEntry>> =
        db.collection("users")
            .snapshots()
            .map { querySnap ->
                mapLeaderboardDocuments(querySnap.documents, limit)
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                val root = fsError ?: error
                handleFirestoreError(root, OperationType.LIST, "users", auth)
                Log.w(TAG, "Error observing fallback users leaderboard", root)
                emit(emptyList())
            }

    private fun mapLeaderboardDocuments(
        documents: List<DocumentSnapshot>,
        limit: Long
    ): List<CloudLeaderboardEntry> {
        val byUser = linkedMapOf<String, Pair<Boolean, CloudLeaderboardEntry>>()
        for (doc in documents) {
            val entry = try {
                doc.toLeaderboardEntryOrNull()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse user for leaderboard: ${doc.id}", e)
                null
            } ?: continue
            val isNestedUnderUsers = doc.reference.path.startsWith("users/")
            val existing = byUser[entry.userId]
            if (existing == null || (isNestedUnderUsers && !existing.first) ||
                (isNestedUnderUsers == existing.first && entry.xp > existing.second.xp)
            ) {
                byUser[entry.userId] = isNestedUnderUsers to entry
            }
        }
        return byUser.values
            .map { it.second }
            .sortedWith(
                compareByDescending<CloudLeaderboardEntry> { it.xp }
                    .thenByDescending { it.totalSolved }
                    .thenBy { it.displayName.lowercase() }
            )
            .take(limit.toInt().coerceAtLeast(1))
    }

    private fun DocumentSnapshot.toLeaderboardEntryOrNull(): CloudLeaderboardEntry? {
        val data = data ?: return null
        val uid = (data["userId"] as? String)?.takeIf { it.isNotBlank() } ?: id
        val displayName = (data["displayName"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: (data["name"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: "Tarixchi"
        val avatar = (data["avatar"] as? String)?.trim().orEmpty()
        val rawInitials = (data["initials"] as? String)?.trim().orEmpty()
        val initials = when {
            rawInitials.isNotBlank() -> rawInitials.take(8)
            avatar.isNotBlank() -> avatar.take(8)
            else -> computeInitials(displayName, "")
        }
        val xp = ((data["xp"] as? Number)?.toLong()
            ?: (data["xp"] as? String)?.toLongOrNull()
            ?: (data["score"] as? Number)?.toLong()
            ?: (data["score"] as? String)?.toLongOrNull()
            ?: 0L).coerceAtLeast(0L)
        val streak = ((data["streak"] as? Number)?.toLong() ?: 0L).coerceAtLeast(0L)
        val totalSolved = ((data["totalSolved"] as? Number)?.toLong() ?: 0L).coerceAtLeast(0L)
        val correctCount = ((data["correctCount"] as? Number)?.toLong() ?: 0L).coerceIn(0L, totalSolved)
        val explicitAccuracy = (data["accuracy"] as? Number)?.toLong()
        val accuracy = explicitAccuracy?.coerceIn(0L, 100L)
            ?: if (totalSolved > 0L) ((correctCount * 100L) / totalSolved).coerceIn(0L, 100L) else 0L
        val explicitMinutes = (data["timeSpentMinutes"] as? Number)?.toLong()
        val timeSpentSec = ((data["timeSpentSeconds"] as? Number)?.toLong() ?: 0L).coerceAtLeast(0L)
        val timeSpentMinutes = (explicitMinutes ?: (timeSpentSec / 60L)).coerceAtLeast(0L)
        val grade = ((data["grade"] as? Number)?.toLong() ?: 7L).coerceIn(6L, 11L)
        val createdAt = runCatching {
            getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }.getOrNull()
        val updatedAt = runCatching {
            getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }.getOrNull()
        return CloudLeaderboardEntry(
            docId = id,
            userId = uid,
            displayName = displayName,
            initials = initials,
            xp = xp,
            streak = streak,
            totalSolved = totalSolved,
            correctCount = correctCount,
            accuracy = accuracy,
            timeSpentMinutes = timeSpentMinutes,
            grade = grade,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * Kuniga 1 marta Firebase'dan foydalanuvchi tarixini (attempts) o'qiydi va bugun lokal
     * ishlangan testlar (pendingQuizAttempts) bilan birlashtirib qaytaradi.
     */
    fun observeUserAttempts(userId: String, limit: Long = 200L): Flow<List<CloudQuizAttempt>> =
        preferences.pendingQuizAttempts.map { pendingList ->
            val cacheKey = "${todayDateKey()}_$userId"
            val remoteBase = dailyAttemptsMemoryCache[cacheKey] ?: run {
                val fetched = try {
                    val snap = withTimeoutOrNull(5000) {
                        db.collection("users").document(userId)
                            .collection("attempts")
                            .orderBy("createdAt", Query.Direction.DESCENDING)
                            .limit(limit)
                            .get()
                            .await()
                    }
                    snap?.documents?.mapNotNull { doc ->
                        try {
                            doc.toObject(
                                CloudQuizAttempt::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                        } catch (e: Exception) {
                            null
                        }
                    } ?: emptyList()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.LIST, "users/$userId/attempts", auth)
                    emptyList()
                }
                dailyAttemptsMemoryCache[cacheKey] = fetched
                fetched
            }
            val localMapped = pendingList.sortedByDescending { it.createdAtMs }.map { p ->
                CloudQuizAttempt(
                    docId = p.attemptId,
                    categoryKey = p.categoryKey,
                    categoryTitle = p.categoryTitle,
                    grade = p.grade.toLong(),
                    solvedCount = p.solvedCount.toLong(),
                    correctCount = p.correctCount.toLong(),
                    durationSeconds = p.durationSeconds.toLong(),
                    createdAt = Timestamp(p.createdAtMs / 1000, ((p.createdAtMs % 1000) * 1_000_000).toInt())
                )
            }
            (localMapped + remoteBase).distinctBy { it.docId }.take(limit.toInt())
        }
}
