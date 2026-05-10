package com.glassbox.hello.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.glassbox.hello.database.entities.ProfileEntity
import com.glassbox.hello.repository.ProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * ViewModel for browser profile management.
 */
class ProfileViewModel(
    private val repository: ProfileRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileViewState())
    val state: StateFlow<ProfileViewState> = _state.asStateFlow()

    private var profilesJob: Job? = null
    private var activeProfileJob: Job? = null

    init {
        getProfiles()
        observeActiveProfile()
    }

    /**
     * Observes real-time profile list updates.
     */
    fun getProfiles() {
        profilesJob?.cancel()
        profilesJob = viewModelScope.launch {
            repository.getAllProfiles()
                .catch { error -> handleError("observe profiles", error) }
                .collect { profiles ->
                    _state.update { current ->
                        current.copy(profiles = profiles, isLoading = false)
                    }
                }
        }
    }

    /**
     * Creates a new profile.
     */
    fun createProfile(
        name: String,
        type: String = ProfileEntity.TYPE_CUSTOM,
        email: String? = null,
        activate: Boolean = true
    ) {
        viewModelScope.launch {
            execute("create profile") {
                repository.createProfile(
                    name = name,
                    type = type,
                    email = email,
                    isActive = activate
                )
                _state.update { current -> current.copy(statusMessage = "Profile created") }
            }
        }
    }

    /**
     * Updates an existing profile.
     */
    fun updateProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            execute("update profile") {
                repository.updateProfile(profile)
                _state.update { current -> current.copy(statusMessage = "Profile updated") }
            }
        }
    }

    /**
     * Switches the active profile atomically.
     */
    fun switchProfile(profileId: Int) {
        viewModelScope.launch {
            execute("switch profile") {
                val profile = repository.switchProfile(profileId)
                _state.update { current ->
                    current.copy(activeProfile = profile, selectedProfile = profile, statusMessage = "Profile switched")
                }
            }
        }
    }

    /**
     * Deletes a profile and activates a fallback profile when needed.
     */
    fun deleteProfile(profileId: Int) {
        viewModelScope.launch {
            execute("delete profile") {
                val deleted = repository.deleteProfile(profileId)
                _state.update { current ->
                    current.copy(statusMessage = if (deleted) "Profile deleted" else "Profile not found")
                }
            }
        }
    }

    /**
     * Selects a profile for editing without activating it.
     */
    fun selectProfile(profile: ProfileEntity?) {
        _state.update { current -> current.copy(selectedProfile = profile) }
    }

    /**
     * Enables or disables sync on a profile.
     */
    fun setSyncEnabled(profileId: Int, enabled: Boolean) {
        viewModelScope.launch {
            execute("set profile sync") {
                val profile = repository.setSyncEnabled(profileId, enabled)
                _state.update { current -> current.copy(selectedProfile = profile, statusMessage = "Sync updated") }
            }
        }
    }

    /**
     * Updates OAuth token metadata on a profile.
     */
    fun updateOAuthTokens(
        profileId: Int,
        accessToken: String?,
        refreshToken: String?,
        tokenExpiry: Long?
    ) {
        viewModelScope.launch {
            execute("update OAuth tokens") {
                val profile = repository.updateOAuthTokens(profileId, accessToken, refreshToken, tokenExpiry)
                _state.update { current -> current.copy(selectedProfile = profile, statusMessage = "Tokens updated") }
            }
        }
    }

    /**
     * Marks the profile as synced now.
     */
    fun markSynced(profileId: Int) {
        viewModelScope.launch {
            execute("mark synced") {
                val profile = repository.markSynced(profileId)
                _state.update { current -> current.copy(selectedProfile = profile, statusMessage = "Profile synced") }
            }
        }
    }

    /**
     * Clears the current error message.
     */
    fun clearError() {
        _state.update { current -> current.copy(errorMessage = null) }
    }

    /**
     * Clears the current transient status message.
     */
    fun clearStatusMessage() {
        _state.update { current -> current.copy(statusMessage = null) }
    }

    override fun onCleared() {
        profilesJob?.cancel()
        activeProfileJob?.cancel()
        super.onCleared()
    }

    private fun observeActiveProfile() {
        activeProfileJob?.cancel()
        activeProfileJob = viewModelScope.launch {
            repository.getActiveProfile()
                .catch { error -> handleError("observe active profile", error) }
                .collect { profile ->
                    _state.update { current -> current.copy(activeProfile = profile) }
                }
        }
    }

    private suspend fun <T> execute(operation: String, block: suspend () -> T): T? {
        _state.update { current -> current.copy(isLoading = true, errorMessage = null) }
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleError(operation, error)
            null
        } finally {
            _state.update { current -> current.copy(isLoading = false) }
        }
    }

    private fun handleError(operation: String, error: Throwable) {
        if (error is CancellationException) throw error
        Log.e(TAG, "Profile ViewModel failure during $operation.", error)
        _state.update { current ->
            current.copy(
                isLoading = false,
                errorMessage = error.message ?: "Profile operation failed."
            )
        }
    }

    companion object {
        private const val TAG: String = "ProfileViewModel"

        /**
         * Creates a factory backed by the application database.
         */
        fun factory(context: Context): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
                        return ProfileViewModel(ProfileRepository.create(applicationContext)) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}

/**
 * Immutable profile management state.
 */
data class ProfileViewState(
    val profiles: List<ProfileEntity> = emptyList(),
    val activeProfile: ProfileEntity? = null,
    val selectedProfile: ProfileEntity? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)
