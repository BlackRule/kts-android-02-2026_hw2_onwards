package com.example.myapplication.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.myapplication.core.di.LocalAppContainer
import com.example.myapplication.feature.shopPicker.model.ShopItem
import com.example.myapplication.feature.itemCatalog.presentation.CreateItemScreen
import com.example.myapplication.feature.itemCatalog.presentation.ItemSelectScreen
import com.example.myapplication.feature.itemCatalog.presentation.NeedBarcodeFilledScreen
import com.example.myapplication.feature.login.presentation.LoginScreen
import com.example.myapplication.feature.onboarding.presentation.WelcomeScreen
import com.example.myapplication.feature.profile.presentation.ProfileScreen
import com.example.myapplication.feature.shoppingList.presentation.ShoppingListScreen
import com.example.myapplication.feature.shoppingLists.presentation.ShoppingListsScreen
import com.example.myapplication.feature.shopCreation.presentation.CreateShopScreen
import com.example.myapplication.feature.shopPicker.presentation.ShopPickerScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    val appContainer = LocalAppContainer.current
    val sessionState = appContainer.sessionRepository.sessionState.collectAsState().value
    val coroutineScope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(AppDestination.Welcome.route) {
            WelcomeScreen(
                onContinue = {
                    coroutineScope.launch {
                        appContainer.sessionRepository.markOnboardingCompleted()
                        navController.navigate(
                            if (sessionState.isLoggedIn) {
                                AppDestination.ShoppingLists.route
                            } else {
                                AppDestination.Login.route
                            },
                        ) {
                            popUpTo(AppDestination.Welcome.route) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
        composable(AppDestination.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(AppDestination.ShoppingLists.route) {
                        popUpTo(AppDestination.Login.route) {
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
                onOpenProfile = {
                    navController.navigate(AppDestination.Profile.route)
                },
            )
        }
        composable(AppDestination.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(AppDestination.Login.route) {
                        popUpTo(AppDestination.ShoppingLists.route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
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
                onOpenItemSelect = { ownerKey, rowId ->
                    navController.navigate(
                        AppDestination.ItemSelect.createRoute(
                            ownerKey = ownerKey,
                            rowId = rowId,
                        ),
                    )
                },
                onOpenNeedBarcode = {
                    navController.navigate(AppDestination.NeedBarcodeFilled.route)
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
        composable(
            route = AppDestination.ItemSelect.route,
            arguments = listOf(
                navArgument(AppDestination.ItemSelect.OWNER_KEY_ARGUMENT) {
                    type = NavType.LongType
                    defaultValue = AppDestination.ItemSelect.noIdDefaultValue()
                },
                navArgument(AppDestination.ItemSelect.ROW_ID_ARGUMENT) {
                    type = NavType.LongType
                    defaultValue = AppDestination.ItemSelect.noIdDefaultValue()
                },
                navArgument(AppDestination.ItemSelect.PENDING_ITEM_ID_ARGUMENT) {
                    type = NavType.LongType
                    defaultValue = AppDestination.ItemSelect.noIdDefaultValue()
                },
                navArgument(AppDestination.ItemSelect.INITIAL_QUERY_ARGUMENT) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            ItemSelectScreen(
                ownerKey = AppDestination.ItemSelect.parseOptionalId(
                    backStackEntry.arguments?.getLong(AppDestination.ItemSelect.OWNER_KEY_ARGUMENT)
                        ?: AppDestination.ItemSelect.noIdDefaultValue(),
                ),
                rowId = AppDestination.ItemSelect.parseOptionalId(
                    backStackEntry.arguments?.getLong(AppDestination.ItemSelect.ROW_ID_ARGUMENT)
                        ?: AppDestination.ItemSelect.noIdDefaultValue(),
                ),
                pendingItemId = AppDestination.ItemSelect.parseOptionalId(
                    backStackEntry.arguments?.getLong(AppDestination.ItemSelect.PENDING_ITEM_ID_ARGUMENT)
                        ?: AppDestination.ItemSelect.noIdDefaultValue(),
                ),
                initialQuery = backStackEntry.arguments
                    ?.getString(AppDestination.ItemSelect.INITIAL_QUERY_ARGUMENT)
                    .orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenCreateItem = { ownerKey, rowId, pendingItemId, initialQuery, soldByWeight ->
                    navController.navigate(
                        AppDestination.CreateItem.createRoute(
                            ownerKey = ownerKey,
                            rowId = rowId,
                            pendingItemId = pendingItemId,
                            initialQuery = initialQuery,
                            soldByWeight = soldByWeight,
                        ),
                    )
                },
            )
        }
        composable(
            route = AppDestination.CreateItem.route,
            arguments = listOf(
                navArgument(AppDestination.CreateItem.OWNER_KEY_ARGUMENT) {
                    type = NavType.LongType
                    defaultValue = AppDestination.CreateItem.noIdDefaultValue()
                },
                navArgument(AppDestination.CreateItem.ROW_ID_ARGUMENT) {
                    type = NavType.LongType
                    defaultValue = AppDestination.CreateItem.noIdDefaultValue()
                },
                navArgument(AppDestination.CreateItem.PENDING_ITEM_ID_ARGUMENT) {
                    type = NavType.LongType
                    defaultValue = AppDestination.CreateItem.noIdDefaultValue()
                },
                navArgument(AppDestination.CreateItem.INITIAL_QUERY_ARGUMENT) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(AppDestination.CreateItem.SOLD_BY_WEIGHT_ARGUMENT) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            CreateItemScreen(
                ownerKey = AppDestination.CreateItem.parseOptionalId(
                    backStackEntry.arguments?.getLong(AppDestination.CreateItem.OWNER_KEY_ARGUMENT)
                        ?: AppDestination.CreateItem.noIdDefaultValue(),
                ),
                rowId = AppDestination.CreateItem.parseOptionalId(
                    backStackEntry.arguments?.getLong(AppDestination.CreateItem.ROW_ID_ARGUMENT)
                        ?: AppDestination.CreateItem.noIdDefaultValue(),
                ),
                pendingItemId = AppDestination.CreateItem.parseOptionalId(
                    backStackEntry.arguments?.getLong(AppDestination.CreateItem.PENDING_ITEM_ID_ARGUMENT)
                        ?: AppDestination.CreateItem.noIdDefaultValue(),
                ),
                initialQuery = backStackEntry.arguments
                    ?.getString(AppDestination.CreateItem.INITIAL_QUERY_ARGUMENT)
                    .orEmpty(),
                soldByWeight = backStackEntry.arguments
                    ?.getBoolean(AppDestination.CreateItem.SOLD_BY_WEIGHT_ARGUMENT)
                    ?: false,
                onBack = { navController.popBackStack() },
                onFinished = {
                    navController.popBackStack()
                    navController.popBackStack()
                },
            )
        }
        composable(AppDestination.NeedBarcodeFilled.route) {
            NeedBarcodeFilledScreen(
                onBack = { navController.popBackStack() },
                onOpenItemSelect = { pendingItemId, initialQuery ->
                    navController.navigate(
                        AppDestination.ItemSelect.createRoute(
                            pendingItemId = pendingItemId,
                            initialQuery = initialQuery,
                        ),
                    )
                },
            )
        }
    }
}

private val navigationJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
