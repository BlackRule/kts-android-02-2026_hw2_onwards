package com.example.myapplication.core.navigation

import io.ktor.http.encodeURLQueryComponent

sealed class AppDestination(val route: String) {
    data object Welcome : AppDestination("welcome")
    data object Login : AppDestination("login")
    data object ShoppingLists : AppDestination("shoppingLists")
    data object ShoppingList : AppDestination("shoppingList?shoppingListId={shoppingListId}") {
        const val BASE_ROUTE = "shoppingList"
        const val SHOPPING_LIST_ID_ARGUMENT = "shoppingListId"
        const val SELECTED_SHOP_RESULT_KEY = "selectedShopResult"
        private const val NO_ID = -1L

        fun createRoute(shoppingListId: Long? = null): String {
            return if (shoppingListId == null) {
                BASE_ROUTE
            } else {
                "$BASE_ROUTE?$SHOPPING_LIST_ID_ARGUMENT=$shoppingListId"
            }
        }

        fun parseShoppingListId(argumentValue: Long): Long? {
            return argumentValue.takeUnless { it == NO_ID }
        }

        fun noIdDefaultValue(): Long = NO_ID
    }

    data object ShopPicker : AppDestination("shopPicker?query={query}&selectionMode={selectionMode}") {
        const val BASE_ROUTE = "shopPicker"
        const val QUERY_ARGUMENT = "query"
        const val SELECTION_MODE_ARGUMENT = "selectionMode"

        fun createRoute(
            query: String = "",
            selectionMode: Boolean = false,
        ): String {
            val parameters = buildList {
                if (query.isNotBlank()) {
                    add("$QUERY_ARGUMENT=${query.encodeURLQueryComponent()}")
                }
                if (selectionMode) {
                    add("$SELECTION_MODE_ARGUMENT=true")
                }
            }

            return if (parameters.isEmpty()) {
                BASE_ROUTE
            } else {
                "$BASE_ROUTE?${parameters.joinToString("&")}"
            }
        }
    }

    data object CreateShop : AppDestination("createShop?selectionMode={selectionMode}") {
        const val BASE_ROUTE = "createShop"
        const val SELECTION_MODE_ARGUMENT = "selectionMode"

        fun createRoute(returnToSelectionMode: Boolean = false): String {
            return if (returnToSelectionMode) {
                "$BASE_ROUTE?$SELECTION_MODE_ARGUMENT=true"
            } else {
                BASE_ROUTE
            }
        }
    }

    data object ItemSelect : AppDestination(
        "itemSelect?ownerKey={ownerKey}&rowId={rowId}&pendingItemId={pendingItemId}&initialQuery={initialQuery}",
    ) {
        const val BASE_ROUTE = "itemSelect"
        const val OWNER_KEY_ARGUMENT = "ownerKey"
        const val ROW_ID_ARGUMENT = "rowId"
        const val PENDING_ITEM_ID_ARGUMENT = "pendingItemId"
        const val INITIAL_QUERY_ARGUMENT = "initialQuery"
        private const val NO_ID = -1L

        fun createRoute(
            ownerKey: Long? = null,
            rowId: Long? = null,
            pendingItemId: Long? = null,
            initialQuery: String = "",
        ): String {
            val parameters = buildList {
                ownerKey?.let { add("$OWNER_KEY_ARGUMENT=$it") }
                rowId?.let { add("$ROW_ID_ARGUMENT=$it") }
                pendingItemId?.let { add("$PENDING_ITEM_ID_ARGUMENT=$it") }
                if (initialQuery.isNotBlank()) {
                    add("$INITIAL_QUERY_ARGUMENT=${initialQuery.encodeURLQueryComponent()}")
                }
            }
            return if (parameters.isEmpty()) {
                BASE_ROUTE
            } else {
                "$BASE_ROUTE?${parameters.joinToString("&")}"
            }
        }

        fun parseOptionalId(argumentValue: Long): Long? {
            return argumentValue.takeUnless { it == NO_ID }
        }

        fun noIdDefaultValue(): Long = NO_ID
    }

    data object CreateItem : AppDestination(
        "createItem?ownerKey={ownerKey}&rowId={rowId}&pendingItemId={pendingItemId}&initialQuery={initialQuery}&soldByWeight={soldByWeight}",
    ) {
        const val BASE_ROUTE = "createItem"
        const val OWNER_KEY_ARGUMENT = "ownerKey"
        const val ROW_ID_ARGUMENT = "rowId"
        const val PENDING_ITEM_ID_ARGUMENT = "pendingItemId"
        const val INITIAL_QUERY_ARGUMENT = "initialQuery"
        const val SOLD_BY_WEIGHT_ARGUMENT = "soldByWeight"
        private const val NO_ID = -1L

        fun createRoute(
            ownerKey: Long? = null,
            rowId: Long? = null,
            pendingItemId: Long? = null,
            initialQuery: String = "",
            soldByWeight: Boolean = false,
        ): String {
            val parameters = buildList {
                ownerKey?.let { add("$OWNER_KEY_ARGUMENT=$it") }
                rowId?.let { add("$ROW_ID_ARGUMENT=$it") }
                pendingItemId?.let { add("$PENDING_ITEM_ID_ARGUMENT=$it") }
                if (initialQuery.isNotBlank()) {
                    add("$INITIAL_QUERY_ARGUMENT=${initialQuery.encodeURLQueryComponent()}")
                }
                if (soldByWeight) {
                    add("$SOLD_BY_WEIGHT_ARGUMENT=true")
                }
            }
            return if (parameters.isEmpty()) {
                BASE_ROUTE
            } else {
                "$BASE_ROUTE?${parameters.joinToString("&")}"
            }
        }

        fun parseOptionalId(argumentValue: Long): Long? {
            return argumentValue.takeUnless { it == NO_ID }
        }

        fun noIdDefaultValue(): Long = NO_ID
    }

    data object NeedBarcodeFilled : AppDestination("needBarcodeFilled")
}
