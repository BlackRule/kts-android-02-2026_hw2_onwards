package com.example.myapplication.core.session

import com.example.myapplication.feature.profile.model.LoggedInProfile
import kotlinx.coroutines.flow.StateFlow

data class SessionState(
    val isLoaded: Boolean = false,
    val isOnboardingCompleted: Boolean = false,
    val authToken: String? = null,
    val profile: LoggedInProfile? = null,
) {
    val isLoggedIn: Boolean
        get() = !authToken.isNullOrBlank()
}

interface SessionRepository {
    val sessionState: StateFlow<SessionState>

    suspend fun markOnboardingCompleted()

    suspend fun saveSession(
        token: String,
        profile: LoggedInProfile,
    )

    suspend fun clearSession()
}
