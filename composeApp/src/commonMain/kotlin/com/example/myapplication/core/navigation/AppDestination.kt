package com.example.myapplication.core.navigation

import io.ktor.http.encodeURLQueryComponent

sealed class AppDestination(val route: String) {
    data object Welcome : AppDestination("welcome")
    data object Login : AppDestination("login")
    data object ShopPicker : AppDestination("shopPicker?query={query}") {
        const val BASE_ROUTE = "shopPicker"
        const val QUERY_ARGUMENT = "query"

        fun createRoute(query: String = ""): String {
            return if (query.isBlank()) {
                BASE_ROUTE
            } else {
                "$BASE_ROUTE?$QUERY_ARGUMENT=${query.encodeURLQueryComponent()}"
            }
        }
    }

    data object CreateShop : AppDestination("createShop")
}
