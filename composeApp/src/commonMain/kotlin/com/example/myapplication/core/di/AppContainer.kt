package com.example.myapplication.core.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.myapplication.core.session.SessionRepository
import com.example.myapplication.feature.login.repository.LoginRepository
import com.example.myapplication.feature.shopPicker.repository.ShopsRepository

interface AppDataCleaner {
    suspend fun clear()
}

interface AppContainer {
    val sessionRepository: SessionRepository
    val loginRepository: LoginRepository
    val shopsRepository: ShopsRepository
    val appDataCleaner: AppDataCleaner
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is not provided")
}

@Composable
expect fun ProvideAppContainer(content: @Composable () -> Unit)
