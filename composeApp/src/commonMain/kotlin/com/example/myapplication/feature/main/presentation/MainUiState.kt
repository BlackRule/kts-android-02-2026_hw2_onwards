package com.example.myapplication.feature.main.presentation

import com.example.myapplication.feature.main.model.UserItem

data class MainUiState(
    val users: List<UserItem> = emptyList(),
)
