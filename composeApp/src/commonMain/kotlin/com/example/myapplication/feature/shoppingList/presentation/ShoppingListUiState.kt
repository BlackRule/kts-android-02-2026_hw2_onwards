package com.example.myapplication.feature.shoppingList.presentation

import com.example.myapplication.feature.shopPicker.model.ShopItem

data class ShoppingListUiState(
    val shoppingListId: Long? = null,
    val selectedShop: ShopItem? = null,
    val paidAtInput: String = "",
    val totalInput: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saveSucceeded: Boolean = false,
)
