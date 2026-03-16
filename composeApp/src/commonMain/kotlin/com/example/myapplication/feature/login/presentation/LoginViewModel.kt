package com.example.myapplication.feature.login.presentation

import androidx.lifecycle.ViewModel
import com.example.myapplication.common.ui.UiText
import com.example.myapplication.core.session.SessionRepository
import com.example.myapplication.common.ui.toUiTextOr
import com.example.myapplication.feature.login.repository.LoginRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.login_invalid_credentials_error

class LoginViewModel(
    private val loginRepository: LoginRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<LoginUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<LoginUiEvent> = _events.asSharedFlow()

    fun onUsernameChanged(username: String) {
        _state.update { currentState ->
            val updatedState = currentState.copy(username = username, error = null)
            updatedState.copy(
                isLoginButtonActive = isLoginButtonActive(
                    username = updatedState.username,
                    password = updatedState.password,
                    isLoggingIn = updatedState.isLoggingIn,
                ),
            )
        }
    }

    fun onPasswordChanged(password: String) {
        _state.update { currentState ->
            val updatedState = currentState.copy(password = password, error = null)
            updatedState.copy(
                isLoginButtonActive = isLoginButtonActive(
                    username = updatedState.username,
                    password = updatedState.password,
                    isLoggingIn = updatedState.isLoggingIn,
                ),
            )
        }
    }

    fun onLoginClicked() {
        val currentState = _state.value
        if (currentState.isLoggingIn) {
            return
        }

        screenScope.launch {
            _state.update { state ->
                state.copy(
                    isLoggingIn = true,
                    isLoginButtonActive = isLoginButtonActive(
                        username = state.username,
                        password = state.password,
                        isLoggingIn = true,
                    ),
                    error = null,
                )
            }

            val result = loginRepository.login(
                username = currentState.username,
                password = currentState.password,
            ).mapCatching { loginResult ->
                sessionRepository.saveSession(
                    token = loginResult.token,
                    profile = loginResult.profile,
                )
                loginResult
            }

            if (result.isSuccess) {
                _state.update { state ->
                    state.copy(
                        isLoggingIn = false,
                        isLoginButtonActive = isLoginButtonActive(
                            username = state.username,
                            password = state.password,
                            isLoggingIn = false,
                        ),
                        error = null,
                    )
                }
                _events.tryEmit(LoginUiEvent.LoginSuccessEvent)
            } else {
                _state.update { state ->
                    state.copy(
                        isLoggingIn = false,
                        isLoginButtonActive = isLoginButtonActive(
                            username = state.username,
                            password = state.password,
                            isLoggingIn = false,
                        ),
                        error = result.exceptionOrNull().toUiTextOr(
                            fallback = Res.string.login_invalid_credentials_error,
                        ),
                    )
                }
            }
        }
    }

    private fun isLoginButtonActive(
        username: String,
        password: String,
        isLoggingIn: Boolean,
    ): Boolean = username.isNotBlank() && password.isNotBlank() && !isLoggingIn

    override fun onCleared() {
        screenScope.cancel()
        super.onCleared()
    }
}
