package com.example.myapplication.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.myapplication.feature.shopPicker.model.ShopItem
import com.example.myapplication.feature.login.presentation.LoginScreen
import com.example.myapplication.feature.onboarding.presentation.WelcomeScreen
import com.example.myapplication.feature.shoppingList.presentation.ShoppingListScreen
import com.example.myapplication.feature.shoppingLists.presentation.ShoppingListsScreen
import com.example.myapplication.feature.shopCreation.presentation.CreateShopScreen
import com.example.myapplication.feature.shopPicker.presentation.ShopPickerScreen
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

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
                    navController.navigate(AppDestination.ShoppingLists.route) {
                        popUpTo(AppDestination.Welcome.route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(AppDestination.ShoppingLists.route) {
            ShoppingListsScreen(
                onCreateShoppingList = {
                    navController.navigate(AppDestination.ShoppingList.createRoute())
                },
                onEditShoppingList = { shoppingListId ->
                    navController.navigate(AppDestination.ShoppingList.createRoute(shoppingListId))
                },
            )
        }
        composable(
            route = AppDestination.ShoppingList.route,
            arguments = listOf(
                navArgument(AppDestination.ShoppingList.SHOPPING_LIST_ID_ARGUMENT) {
                    type = NavType.LongType
                    defaultValue = AppDestination.ShoppingList.noIdDefaultValue()
                },
            ),
        ) { backStackEntry ->
            val shoppingListId = AppDestination.ShoppingList.parseShoppingListId(
                backStackEntry.arguments
                    ?.getLong(AppDestination.ShoppingList.SHOPPING_LIST_ID_ARGUMENT)
                    ?: AppDestination.ShoppingList.noIdDefaultValue(),
            )
            val selectedShopPayload = backStackEntry.savedStateHandle
                .getStateFlow<String?>(AppDestination.ShoppingList.SELECTED_SHOP_RESULT_KEY, null)
                .collectAsState()
                .value

            ShoppingListScreen(
                shoppingListId = shoppingListId,
                selectedShopPayload = selectedShopPayload,
                onSelectedShopConsumed = {
                    backStackEntry.savedStateHandle.remove<String>(AppDestination.ShoppingList.SELECTED_SHOP_RESULT_KEY)
                },
                onBack = { navController.popBackStack() },
                onSelectShop = {
                    navController.navigate(
                        AppDestination.ShopPicker.createRoute(selectionMode = true),
                    )
                },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = AppDestination.ShopPicker.route,
            arguments = listOf(
                navArgument(AppDestination.ShopPicker.QUERY_ARGUMENT) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(AppDestination.ShopPicker.SELECTION_MODE_ARGUMENT) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val initialQuery = backStackEntry.arguments
                ?.getString(AppDestination.ShopPicker.QUERY_ARGUMENT)
                .orEmpty()
            val selectionMode = backStackEntry.arguments
                ?.getBoolean(AppDestination.ShopPicker.SELECTION_MODE_ARGUMENT)
                ?: false

            ShopPickerScreen(
                initialQuery = initialQuery,
                onAddNewShop = {
                    navController.navigate(
                        AppDestination.CreateShop.createRoute(returnToSelectionMode = selectionMode),
                    )
                },
                onShopSelected = if (selectionMode) {
                    { shop: ShopItem ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(
                                AppDestination.ShoppingList.SELECTED_SHOP_RESULT_KEY,
                                navigationJson.encodeToString(shop),
                            )
                        navController.popBackStack()
                    }
                } else {
                    null
                },
            )
        }
        composable(
            route = AppDestination.CreateShop.route,
            arguments = listOf(
                navArgument(AppDestination.CreateShop.SELECTION_MODE_ARGUMENT) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val returnToSelectionMode = backStackEntry.arguments
                ?.getBoolean(AppDestination.CreateShop.SELECTION_MODE_ARGUMENT)
                ?: false

            CreateShopScreen(
                returnToSelectionMode = returnToSelectionMode,
                onBack = { navController.popBackStack() },
                onShopCreated = { createdShopName ->
                    navController.navigate(
                        AppDestination.ShopPicker.createRoute(
                            query = createdShopName,
                            selectionMode = returnToSelectionMode,
                        ),
                    ) {
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

private val navigationJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
