package com.example.myapplication.core.session

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapplication.feature.profile.model.LoggedInProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private val Context.appDataStore by preferencesDataStore(name = "app_preferences")

internal class DataStoreSessionRepository private constructor(
    context: Context,
) : SessionRepository {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val sessionState: StateFlow<SessionState> = appContext.appDataStore.data
        .map(::toSessionState)
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = SessionState(),
        )

    override suspend fun markOnboardingCompleted() {
        appContext.appDataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = true
        }
    }

    override suspend fun saveSession(
        token: String,
        profile: LoggedInProfile,
    ) {
        appContext.appDataStore.edit { preferences ->
            preferences[AUTH_TOKEN_KEY] = token
            preferences[PROFILE_JSON_KEY] = sessionJson.encodeToString(profile)
        }
    }

    override suspend fun clearSession() {
        appContext.appDataStore.edit { preferences ->
            preferences.remove(AUTH_TOKEN_KEY)
            preferences.remove(PROFILE_JSON_KEY)
        }
    }

    private fun toSessionState(preferences: Preferences): SessionState {
        val profile = preferences[PROFILE_JSON_KEY]?.let { serializedProfile ->
            runCatching {
                sessionJson.decodeFromString<LoggedInProfile>(serializedProfile)
            }.getOrNull()
        }

        return SessionState(
            isLoaded = true,
            isOnboardingCompleted = preferences[ONBOARDING_COMPLETED_KEY] ?: false,
            authToken = preferences[AUTH_TOKEN_KEY],
            profile = profile,
        )
    }

    companion object {
        private val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
        private val PROFILE_JSON_KEY = stringPreferencesKey("profile_json")
        private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
        private val sessionJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        @Volatile
        private var instance: DataStoreSessionRepository? = null

        fun getInstance(context: Context): DataStoreSessionRepository {
            return instance ?: synchronized(this) {
                instance ?: DataStoreSessionRepository(context).also { created ->
                    instance = created
                }
            }
        }
    }
}

internal fun dataStoreSessionRepository(context: Context): SessionRepository {
    return DataStoreSessionRepository.getInstance(context)
}
