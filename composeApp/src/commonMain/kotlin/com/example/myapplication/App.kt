package com.example.myapplication

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.common.ui.theme.AppTheme
import com.example.myapplication.core.di.LocalAppContainer
import com.example.myapplication.core.di.ProvideAppContainer
import com.example.myapplication.core.navigation.AppDestination
import com.example.myapplication.core.navigation.AppNavHost

@Composable
fun App() {
    AppTheme {
        ProvideAppContainer {
            val sessionState by LocalAppContainer.current.sessionRepository.sessionState.collectAsState()
            val navController = rememberNavController()

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                if (sessionState.isLoaded) {
                    AppNavHost(
                        navController = navController,
                        startDestination = when {
                            !sessionState.isOnboardingCompleted -> AppDestination.Welcome.route
                            sessionState.isLoggedIn -> AppDestination.ShoppingLists.route
                            else -> AppDestination.Login.route
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun AppPreview() {
    App()
}
