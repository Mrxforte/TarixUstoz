package com.tarixustoz.app.data.repository

import android.util.Log
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.data.remote.OperationType
import com.tarixustoz.app.data.remote.handleFirestoreError
import com.tarixustoz.app.data.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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

    private fun todayDateKey(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

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
            val snapshot = userRef.get().await()

            if (snapshot.exists()) {
                val cloud = snapshot.toObject(
                    CloudUserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (cloud != null) {
                    val safeName = cloud.displayName.ifBlank {
                        user.displayName ?: user.email?.substringBefore("@") ?: "Tarixchi"
                    }
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
                ensureLeaderboardEntry(uid)
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
                upsertLeaderboardEntry(
                    uid = uid,
                    displayName = defaultName,
                    avatar = initials,
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
            val snap = docRef.get().await()
            if (snap.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "displayName" to name,
                    "bio" to bio,
                    "avatar" to avatar,
                    "phone" to phone,
                    "target" to target,
                    "birthYear" to birthYear,
                    "role" to role,
                    "grade" to grade,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (email.isNotBlank()) updates["email"] = email
                docRef.update(updates).await()
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
                docRef.set(createMap).await()
            }
            upsertLeaderboardEntry(uid, name, avatar, xp, streak, solved, correct, timeSec, grade)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid", auth)
            Result.failure(e)
        }
    }

    /**
     * Kuniga 1 marta Firebase'ga yozish: foydalanuvchi ballari va ishlagan testlari avval lokal
     * navbatga qo'yiladi. Agar bugun Firebase'ga hali yozilmagan bo'lsa, darhol yoziladi;
     * aks holda keyingi kunlik sinxronizatsiyada yoziladi.
     */
    suspend fun pushStatsToCloud(forceImmediate: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.success(Unit)
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
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "displayName" to name,
                    "avatar" to avatar,
                    "grade" to grade,
                    "xp" to xp,
                    "streak" to streak,
                    "totalSolved" to totalSolved,
                    "correctCount" to correctCount,
                    "timeSpentSeconds" to timeSec,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            upsertLeaderboardEntry(uid, name, avatar, xp, streak, totalSolved, correctCount, timeSec, grade)
            preferences.setPendingStatsSync(false)
            preferences.markFirebaseWrittenToday(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            preferences.setPendingStatsSync(true)
            syncScheduler.triggerPendingProgressUpload()
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid", auth)
            Result.failure(e)
        }
    }

    private suspend fun ensureLeaderboardEntry(uid: String) {
        val lbRef = db.collection("leaderboard").document(uid)
        if (!lbRef.get().await().exists()) {
            pushStatsToCloud(forceImmediate = true)
        }
    }

    private suspend fun upsertLeaderboardEntry(
        uid: String,
        displayName: String,
        avatar: String,
        xp: Long,
        streak: Int,
        totalSolved: Int,
        correctCount: Int,
        timeSpentSeconds: Int,
        grade: Int
    ) {
        val safeSolved = totalSolved.coerceAtLeast(0)
        val safeCorrect = correctCount.coerceIn(0, safeSolved)
        val accuracy = if (safeSolved > 0) ((safeCorrect * 100) / safeSolved).coerceIn(0, 100) else 0
        val minutes = (timeSpentSeconds / 60).coerceAtLeast(0)
        val initials = computeInitials(displayName, avatar)

        val lbRef = db.collection("leaderboard").document(uid)
        lbRef.set(
            mapOf(
                "userId" to uid,
                "displayName" to displayName.take(60),
                "initials" to initials,
                "xp" to xp.coerceAtLeast(0L),
                "streak" to streak.coerceAtLeast(0),
                "totalSolved" to safeSolved,
                "correctCount" to safeCorrect,
                "accuracy" to accuracy,
                "timeSpentMinutes" to minutes,
                "grade" to grade.coerceIn(6, 11),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
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

            val snap = docRef.get().await()
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
                preferences.removePendingQuizAttempt(attempt.attemptId)
                wroteAny = true
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
                val existing = docRef.get().await()
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
                preferences.clearCategoryPendingSync(categoryKey)
                wroteAny = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                allSucceeded = false
                handleFirestoreError(e, OperationType.WRITE, "users/$uid/ticketProgress/$docKey", auth)
            }
        }

        if (preferences.pendingStatsSync.first() || wroteAny) {
            val res = pushStatsToCloud(forceImmediate = true)
            if (res.isFailure) allSucceeded = false
            else wroteAny = true
        }

        if (allSucceeded && wroteAny) {
            preferences.markFirebaseWrittenToday(uid)
        }

        allSucceeded
    }

    fun observeLeaderboard(limit: Long = 50L): Flow<List<CloudLeaderboardEntry>> =
        db.collection("leaderboard")
            .orderBy("xp", Query.Direction.DESCENDING)
            .limit(limit)
            .snapshots()
            .map { querySnap ->
                querySnap.toObjects(
                    CloudLeaderboardEntry::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                val root = fsError ?: error
                handleFirestoreError(root, OperationType.LIST, "leaderboard", auth)
                Log.w(TAG, "Error observing leaderboard", root)
                emit(emptyList())
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
                    val snap = db.collection("users").document(userId)
                        .collection("attempts")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(limit)
                        .get()
                        .await()
                    snap.toObjects(
                        CloudQuizAttempt::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
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
