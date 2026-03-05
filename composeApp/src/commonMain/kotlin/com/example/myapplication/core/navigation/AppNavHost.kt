package com.example.myapplication.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.myapplication.feature.login.presentation.LoginScreen
import com.example.myapplication.feature.onboarding.presentation.WelcomeScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.Welcome.route,
        modifier = modifier,
    ) {
        composable(AppDestination.Welcome.route) {
            WelcomeScreen(onContinue = { navController.navigate(AppDestination.Login.route) })
        }
        composable(AppDestination.Login.route) {
            LoginScreen()
        }
    }
}
