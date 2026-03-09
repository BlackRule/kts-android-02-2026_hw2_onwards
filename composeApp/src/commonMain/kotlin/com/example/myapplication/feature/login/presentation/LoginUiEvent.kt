package com.example.myapplication.feature.login.presentation

sealed class LoginUiEvent {
    data object LoginSuccessEvent : LoginUiEvent()
}
