package com.tarixustoz.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.tarixustoz.app.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val preferences: UserPreferencesRepository,
    private val cloudSyncRepository: CloudSyncRepository,
    private val localDb: AppDatabase
) {
    val currentUserFlow: Flow<FirebaseUser?> = callbackFlow {
        trySend(auth.currentUser)
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val currentUser: FirebaseUser?
        get() = try { auth.currentUser } catch (_: Exception) { null }

    suspend fun signInWithGoogleCredential(idToken: String): Result<FirebaseUser?> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user
            if (user != null) {
                preferences.setTestModeActive(false)
                preferences.setIsGuest(false)
                runCatching { cloudSyncRepository.syncOnSignIn(user) }
            }
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser?> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = authResult.user
            if (user != null) {
                preferences.setTestModeActive(false)
                preferences.setIsGuest(false)
                runCatching { cloudSyncRepository.syncOnSignIn(user) }
            }
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(
        name: String,
        email: String,
        password: String,
        role: String,
        grade: Int
    ): Result<FirebaseUser?> {
        return try {
            val cleanName = name.trim().ifBlank { email.substringBefore("@") }.take(60)
            val cleanRole = role.trim().ifBlank { "O'quvchi" }.take(40)
            val authResult = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = authResult.user
            if (user != null) {
                preferences.setTestModeActive(false)
                preferences.setIsGuest(false)
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(cleanName)
                    .build()
                runCatching { user.updateProfile(profileUpdates).await() }
                preferences.setUserRole(cleanRole)
                preferences.setSelectedGrade(grade.coerceIn(6, 11))
                preferences.setDisplayName(cleanName)
                runCatching { cloudSyncRepository.syncOnSignIn(user) }
            }
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncCurrentUserIfNeeded() {
        val user = currentUser ?: return
        cloudSyncRepository.syncOnSignIn(user)
    }

    suspend fun enterTestMode() {
        withContext(Dispatchers.IO + NonCancellable) {
            preferences.setActiveUserUid("local_test_user")
            preferences.setIsGuest(true)
            preferences.setTestModeActive(true)
        }
    }

    suspend fun exitTestMode() {
        withContext(Dispatchers.IO + NonCancellable) {
            preferences.setTestModeActive(false)
        }
    }

    suspend fun signOut() {
        withContext(Dispatchers.IO + NonCancellable) {
            try {
                preferences.setTestModeActive(false)
                preferences.clearUserSessionDataForSwitch()
                localDb.quizAttemptDao().deleteAll()
                localDb.wrongQuestionDao().deleteAll()
                localDb.userProgressDao().deleteAll()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                runCatching { auth.signOut() }
            }
        }
    }
}
