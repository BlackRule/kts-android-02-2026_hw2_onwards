package com.example.myapplication.feature.shoppingLists.presentation

import com.example.myapplication.feature.shoppingLists.model.ShoppingListItem

data class ShoppingListsUiState(
    val shoppingLists: List<ShoppingListItem> = emptyList(),
    val isLoading: Boolean = true,
    val deletingShoppingListId: Long? = null,
    val errorMessage: String? = null,
)
