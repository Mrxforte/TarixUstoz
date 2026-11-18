package com.tarixustoz.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.tarixustoz.app.data.repository.AuthRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val userUid: String? = null,
    val userEmail: String? = null,
    val displayName: String = "Tarixchi",
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val currentUser: StateFlow<FirebaseUser?> = authRepository.currentUserFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepository.currentUser)

    val isTestModeActive: StateFlow<Boolean> = preferencesRepository.isTestModeActive
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUserFlow.collect { user ->
                _uiState.update {
                    it.copy(
                        isSignedIn = user != null,
                        userUid = user?.uid,
                        userEmail = user?.email,
                        displayName = user?.displayName ?: "Tarixchi"
                    )
                }
            }
        }
    }

    fun ensureCloudSynced() {
        viewModelScope.launch {
            authRepository.syncCurrentUserIfNeeded()
        }
    }

    fun setLoading(loading: Boolean) {
        _uiState.update { it.copy(isLoading = loading, errorMessage = null, infoMessage = null) }
    }

    fun setError(message: String?) {
        _uiState.update { it.copy(isLoading = false, errorMessage = message) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun handleGoogleSignInResult(idToken: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = authRepository.signInWithGoogleCredential(idToken)
            if (result.isSuccess && result.getOrNull() != null) {
                _uiState.update { it.copy(isLoading = false, isSignedIn = true) }
                onComplete()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = mapFirebaseAuthError(result.exceptionOrNull())
                    )
                }
            }
        }
    }

    fun signInWithEmail(email: String, password: String, onComplete: () -> Unit) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            _uiState.update { it.copy(errorMessage = "Iltimos, to‘g‘ri email manzilini kiriting") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Parol kamida 6 ta belgidan iborat bo‘lishi kerak") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = authRepository.signInWithEmail(cleanEmail, password)
            if (result.isSuccess && result.getOrNull() != null) {
                _uiState.update { it.copy(isLoading = false, isSignedIn = true) }
                onComplete()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = mapFirebaseAuthError(result.exceptionOrNull())
                    )
                }
            }
        }
    }

    fun signUpWithEmail(
        name: String,
        email: String,
        password: String,
        role: String,
        grade: Int,
        onComplete: () -> Unit
    ) {
        val cleanName = name.trim()
        val cleanEmail = email.trim()
        if (cleanName.length < 2) {
            _uiState.update { it.copy(errorMessage = "Ismingizni kiriting (kamida 2 harf)") }
            return
        }
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            _uiState.update { it.copy(errorMessage = "To‘g‘ri email manzilini kiriting") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Parol kamida 6 ta belgidan iborat bo‘lishi kerak") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = authRepository.signUpWithEmail(cleanName, cleanEmail, password, role, grade)
            if (result.isSuccess && result.getOrNull() != null) {
                _uiState.update { it.copy(isLoading = false, isSignedIn = true) }
                onComplete()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = mapFirebaseAuthError(result.exceptionOrNull())
                    )
                }
            }
        }
    }

    fun enterTestMode(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            authRepository.enterTestMode()
            onComplete()
        }
    }

    fun exitTestMode() {
        viewModelScope.launch {
            authRepository.exitTestMode()
        }
    }

    fun sendPasswordReset(email: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            _uiState.update { it.copy(errorMessage = "Parolni tiklash uchun email manzilingizni kiriting") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = authRepository.sendPasswordReset(cleanEmail)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = "Parolni tiklash havolasi $cleanEmail manziliga yuborildi"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = mapFirebaseAuthError(result.exceptionOrNull())
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    private fun mapFirebaseAuthError(error: Throwable?): String {
        val msg = error?.message.orEmpty()
        return when {
            msg.contains("password is invalid", ignoreCase = true) ||
                msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ->
                "Email yoki parol noto‘g‘ri. Qaytadan tekshiring."
            msg.contains("no user record", ignoreCase = true) ->
                "Bu email bilan hisob topilmadi. Ro‘yxatdan o‘ting."
            msg.contains("already in use", ignoreCase = true) ->
                "Bu email manzili allaqachon ro‘yxatdan o‘tgan."
            msg.contains("badly formatted", ignoreCase = true) ->
                "Email manzili formati noto‘g‘ri."
            msg.contains("network", ignoreCase = true) ->
                "Internet aloqasini tekshiring va qaytadan urinib ko‘ring."
            else -> msg.ifBlank { "Tizimga kirishda xatolik yuz berdi." }
        }
    }
}
