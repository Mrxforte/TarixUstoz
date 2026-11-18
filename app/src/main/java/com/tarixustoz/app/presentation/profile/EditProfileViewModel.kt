package com.tarixustoz.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarixustoz.app.data.repository.AuthRepository
import com.tarixustoz.app.data.repository.CloudSyncRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditProfileUiState(
    val name: String = "",
    val bio: String = "",
    val phone: String = "",
    val target: String = "",
    val avatar: String = "TU",
    val grade: Int = 7,
    val birthYear: String = "",
    val role: String = "O'quvchi", // "O'quvchi" or "O'qituvchi"
    val isLoading: Boolean = false
)

sealed class EditProfileEvent {
    data object SavedSuccess : EditProfileEvent()
    data class ShowMessage(val message: String) : EditProfileEvent()
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val cloudSyncRepository: CloudSyncRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val isGuest: StateFlow<Boolean> = preferencesRepository.isGuest
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val userEmail: StateFlow<String?> = preferencesRepository.userEmail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _eventFlow.emit(EditProfileEvent.ShowMessage("Hisobdan chiqildi"))
        }
    }

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<EditProfileEvent>()
    val eventFlow: SharedFlow<EditProfileEvent> = _eventFlow.asSharedFlow()

    init {
        loadProfileData()
    }

    private fun loadProfileData() {
        viewModelScope.launch {
            val name = preferencesRepository.displayName.first()
            val bio = preferencesRepository.userBio.first()
            val phone = preferencesRepository.userPhone.first()
            val target = preferencesRepository.userTarget.first()
            val avatar = preferencesRepository.userAvatar.first()
            val grade = preferencesRepository.selectedGrade.first()
            val birthYear = preferencesRepository.userBirthYear.first()
            val role = preferencesRepository.userRole.first()

            _uiState.value = EditProfileUiState(
                name = name,
                bio = bio,
                phone = phone,
                target = target,
                avatar = avatar,
                grade = grade,
                birthYear = birthYear,
                role = role
            )
        }
    }

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun onBioChange(bio: String) {
        _uiState.value = _uiState.value.copy(bio = bio)
    }

    fun onPhoneChange(phone: String) {
        _uiState.value = _uiState.value.copy(phone = phone)
    }

    fun onTargetChange(target: String) {
        _uiState.value = _uiState.value.copy(target = target)
    }

    fun onAvatarChange(avatar: String) {
        _uiState.value = _uiState.value.copy(avatar = avatar)
    }

    fun onGradeChange(grade: Int) {
        _uiState.value = _uiState.value.copy(grade = grade)
    }

    fun onBirthYearChange(birthYear: String) {
        _uiState.value = _uiState.value.copy(birthYear = birthYear)
    }

    fun onRoleChange(role: String) {
        _uiState.value = _uiState.value.copy(role = role)
    }

    fun saveProfile() {
        val state = _uiState.value
        if (state.name.trim().isEmpty()) {
            viewModelScope.launch {
                _eventFlow.emit(EditProfileEvent.ShowMessage("Iltimos, ismingizni kiriting"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)

            preferencesRepository.setDisplayName(state.name.trim())
            preferencesRepository.setUserBio(state.bio.trim())
            preferencesRepository.setUserPhone(state.phone.trim())
            preferencesRepository.setUserTarget(state.target.trim())
            preferencesRepository.setUserAvatar(state.avatar)
            preferencesRepository.setSelectedGrade(state.grade)
            preferencesRepository.setUserBirthYear(state.birthYear.trim())
            preferencesRepository.setUserRole(state.role.trim())

            cloudSyncRepository.syncEditedProfileToCloud()

            _uiState.value = _uiState.value.copy(isLoading = false)
            _eventFlow.emit(EditProfileEvent.SavedSuccess)
        }
    }
}
