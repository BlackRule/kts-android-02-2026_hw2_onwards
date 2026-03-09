package com.example.myapplication.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.myapplication.feature.login.presentation.LoginScreen
import com.example.myapplication.feature.onboarding.presentation.WelcomeScreen
import com.example.myapplication.feature.shopCreation.presentation.CreateShopScreen
import com.example.myapplication.feature.shopPicker.presentation.ShopPickerScreen

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
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(AppDestination.ShopPicker.createRoute()) {
                        popUpTo(AppDestination.Welcome.route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = AppDestination.ShopPicker.route,
            arguments = listOf(
                navArgument(AppDestination.ShopPicker.QUERY_ARGUMENT) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val initialQuery = backStackEntry.arguments
                ?.getString(AppDestination.ShopPicker.QUERY_ARGUMENT)
                .orEmpty()

            ShopPickerScreen(
                initialQuery = initialQuery,
                onAddNewShop = {
                    navController.navigate(AppDestination.CreateShop.route)
                },
            )
        }
        composable(AppDestination.CreateShop.route) {
            CreateShopScreen(
                onBack = { navController.popBackStack() },
                onShopCreated = { createdShopName ->
                    navController.navigate(AppDestination.ShopPicker.createRoute(createdShopName)) {
                        popUpTo(AppDestination.ShopPicker.route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
