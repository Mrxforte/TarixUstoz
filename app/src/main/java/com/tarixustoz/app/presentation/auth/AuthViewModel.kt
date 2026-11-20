package com.tarixustoz.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthException
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
    val infoMessage: String? = null,
    val suggestSignUp: Boolean = false
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
        _uiState.update { it.copy(isLoading = loading, errorMessage = null, infoMessage = null, suggestSignUp = false) }
    }

    fun setError(message: String?) {
        _uiState.update { it.copy(isLoading = false, errorMessage = message, suggestSignUp = false) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null, suggestSignUp = false) }
    }

    private fun isValidEmail(email: String): Boolean {
        return Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(email)
    }

    fun handleGoogleSignInResult(idToken: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null, suggestSignUp = false) }
            val result = authRepository.signInWithGoogleCredential(idToken)
            if (result.isSuccess && result.getOrNull() != null) {
                _uiState.update { it.copy(isLoading = false, isSignedIn = true, suggestSignUp = false) }
                onComplete()
            } else {
                val err = result.exceptionOrNull()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = mapFirebaseAuthError(err),
                        suggestSignUp = shouldSuggestSignUp(err)
                    )
                }
            }
        }
    }

    fun signInWithEmail(email: String, password: String, onComplete: () -> Unit) {
        val cleanEmail = email.trim()
        if (!isValidEmail(cleanEmail)) {
            _uiState.update {
                it.copy(
                    errorMessage = "Iltimos, to‘g‘ri email manzilini kiriting",
                    suggestSignUp = false
                )
            }
            return
        }
        if (password.length < 6) {
            _uiState.update {
                it.copy(
                    errorMessage = "Parol kamida 6 ta belgidan iborat bo‘lishi kerak",
                    suggestSignUp = false
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null, suggestSignUp = false) }
            val result = authRepository.signInWithEmail(cleanEmail, password)
            if (result.isSuccess && result.getOrNull() != null) {
                _uiState.update { it.copy(isLoading = false, isSignedIn = true, suggestSignUp = false) }
                onComplete()
            } else {
                val err = result.exceptionOrNull()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = mapFirebaseAuthError(err),
                        suggestSignUp = shouldSuggestSignUp(err)
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
            _uiState.update { it.copy(errorMessage = "Ismingizni kiriting (kamida 2 harf)", suggestSignUp = false) }
            return
        }
        if (!isValidEmail(cleanEmail)) {
            _uiState.update { it.copy(errorMessage = "To‘g‘ri email manzilini kiriting", suggestSignUp = false) }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Parol kamida 6 ta belgidan iborat bo‘lishi kerak", suggestSignUp = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null, suggestSignUp = false) }
            val result = authRepository.signUpWithEmail(cleanName, cleanEmail, password, role, grade)
            if (result.isSuccess && result.getOrNull() != null) {
                _uiState.update { it.copy(isLoading = false, isSignedIn = true, suggestSignUp = false) }
                onComplete()
            } else {
                val err = result.exceptionOrNull()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = mapFirebaseAuthError(err),
                        suggestSignUp = false
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

    private fun shouldSuggestSignUp(error: Throwable?): Boolean {
        val errorCode = (error as? FirebaseAuthException)?.errorCode.orEmpty()
        if (errorCode == "ERROR_USER_NOT_FOUND" || errorCode == "ERROR_INVALID_CREDENTIAL") {
            return true
        }
        val combined = "${error?.message.orEmpty()} ${error?.cause?.message.orEmpty()}"
        return combined.contains("user-not-found", ignoreCase = true) ||
            combined.contains("no user record", ignoreCase = true) ||
            combined.contains("USER_NOT_FOUND", ignoreCase = true) ||
            combined.contains("invalid-credential", ignoreCase = true) ||
            combined.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true)
    }

    private fun mapFirebaseAuthError(error: Throwable?): String {
        val errorCode = (error as? FirebaseAuthException)?.errorCode.orEmpty()
        val msg = error?.message.orEmpty()
        val causeMsg = error?.cause?.message.orEmpty()
        val combined = "$errorCode $msg $causeMsg"
        return when {
            errorCode == "ERROR_USER_NOT_FOUND" ||
                combined.contains("user-not-found", ignoreCase = true) ||
                combined.contains("no user record", ignoreCase = true) ||
                combined.contains("USER_NOT_FOUND", ignoreCase = true) ->
                "Bunday foydalanuvchi topilmadi. Iltimos, avval «Ro‘yxatdan o‘tish» bo‘limidan ro‘yxatdan o‘ting!"

            errorCode == "ERROR_WRONG_PASSWORD" ||
                combined.contains("wrong-password", ignoreCase = true) ||
                combined.contains("INVALID_PASSWORD", ignoreCase = true) ->
                "Kiritilgan parol noto‘g‘ri. Qaytadan tekshirib ko‘ring."

            errorCode == "ERROR_INVALID_CREDENTIAL" ||
                combined.contains("invalid-credential", ignoreCase = true) ||
                combined.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
                combined.contains("password is invalid", ignoreCase = true) ->
                "Email topilmadi yoki parol noto‘g‘ri. Agar ro‘yxatdan o‘tmagan bo‘lsangiz, avval «Ro‘yxatdan o‘tish» bo‘limidan hisob yarating!"

            errorCode == "ERROR_EMAIL_ALREADY_IN_USE" ||
                combined.contains("email-already-in-use", ignoreCase = true) ||
                combined.contains("already in use", ignoreCase = true) ||
                combined.contains("EMAIL_EXISTS", ignoreCase = true) ->
                "Ushbu email manzili allaqachon ro‘yxatdan o‘tgan."

            errorCode == "ERROR_INVALID_EMAIL" ||
                combined.contains("invalid-email", ignoreCase = true) ||
                combined.contains("badly formatted", ignoreCase = true) ||
                combined.contains("INVALID_EMAIL", ignoreCase = true) ->
                "Email manzili formati noto‘g‘ri kiritildi (masalan: foydalanuvchi@gmail.com)."

            errorCode == "ERROR_WEAK_PASSWORD" ||
                combined.contains("weak-password", ignoreCase = true) ||
                combined.contains("WEAK_PASSWORD", ignoreCase = true) ->
                "Parol juda oddiy. Kamida 6 ta belgi yoki harflar aralashmasidan foydalaning."

            errorCode == "ERROR_TOO_MANY_REQUESTS" ||
                combined.contains("too-many-requests", ignoreCase = true) ||
                combined.contains("TOO_MANY_ATTEMPTS", ignoreCase = true) ->
                "Juda ko‘p muvaffaqiyatsiz urinish bo‘ldi. Biroz kutib, qaytadan urining."

            errorCode == "ERROR_USER_DISABLED" ||
                combined.contains("user-disabled", ignoreCase = true) ||
                combined.contains("USER_DISABLED", ignoreCase = true) ->
                "Ushbu hisob vaqtincha muzlatilgan. Qo‘llab-quvvatlash xizmatiga murojaat qiling."

            combined.contains("network", ignoreCase = true) ||
                combined.contains("NETWORK_ERROR", ignoreCase = true) ->
                "Internet aloqasi mavjud emas. Internetni tekshirib, qayta urining."

            combined.contains("app-check", ignoreCase = true) ->
                "Xavfsizlik tekshiruvida xatolik yuz berdi. Qaytadan urining."

            else -> msg.ifBlank { "Tizimda kutilmagan xatolik yuz berdi. Qayta urinib ko‘ring." }
        }
    }
}
