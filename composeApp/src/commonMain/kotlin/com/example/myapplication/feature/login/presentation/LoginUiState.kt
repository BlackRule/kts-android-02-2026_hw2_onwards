package com.example.myapplication.feature.login.presentation

import com.example.myapplication.common.ui.UiText

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoginButtonActive: Boolean = false,
    val isLoggingIn: Boolean = false,
    val error: UiText? = null,
)
