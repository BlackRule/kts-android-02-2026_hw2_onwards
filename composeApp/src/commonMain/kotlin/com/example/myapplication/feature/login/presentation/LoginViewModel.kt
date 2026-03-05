package com.example.myapplication.feature.login.presentation

import androidx.lifecycle.ViewModel
import com.example.myapplication.feature.login.repository.LoginRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LoginViewModel(
    private val loginRepository: LoginRepository = LoginRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onUsernameChanged(username: String) {
        _state.update { currentState ->
            val updatedState = currentState.copy(username = username, error = null)
            updatedState.copy(
                isLoginButtonActive = isLoginButtonActive(
                    username = updatedState.username,
                    password = updatedState.password,
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
                ),
            )
        }
    }

    fun onLoginClicked(): Boolean {
        val currentState = _state.value
        val result = loginRepository.login(
            username = currentState.username,
            password = currentState.password,
        )

        if (result.isSuccess) {
            _state.update { it.copy(error = null) }
            return true
        } else {
            _state.update {
                it.copy(
                    error = result.exceptionOrNull()?.message ?: "Invalid username or password",
                )
            }
        }
        return false
    }

    private fun isLoginButtonActive(
        username: String,
        password: String,
    ): Boolean = username.isNotBlank() && password.isNotBlank()
}
