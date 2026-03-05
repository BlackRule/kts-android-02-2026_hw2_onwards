package com.example.myapplication.feature.main.presentation

import androidx.lifecycle.ViewModel
import com.example.myapplication.feature.main.repository.UsersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(
    usersRepository: UsersRepository = UsersRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState(users = usersRepository.getList()))
    val state: StateFlow<MainUiState> = _state.asStateFlow()
}
