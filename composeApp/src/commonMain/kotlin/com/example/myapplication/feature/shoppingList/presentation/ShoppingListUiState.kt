package com.example.myapplication.feature.shoppingList.presentation

import com.example.myapplication.common.ui.UiText
import com.example.myapplication.feature.shopPicker.model.ShopItem

data class ShoppingListUiState(
    val shoppingListId: Long? = null,
    val selectedShop: ShopItem? = null,
    val paidAtInput: String = "",
    val totalDisplay: String = "0.00",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: UiText? = null,
    val saveSucceeded: Boolean = false,
)
