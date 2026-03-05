package com.example.myapplication.core.navigation

sealed class AppDestination(val route: String) {
    data object Welcome : AppDestination("welcome")
    data object Login : AppDestination("login")
    data object Main : AppDestination("main")
}
