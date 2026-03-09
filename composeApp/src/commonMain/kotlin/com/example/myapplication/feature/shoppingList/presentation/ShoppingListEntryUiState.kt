package com.example.myapplication.feature.shoppingList.presentation

data class ShoppingListEntryUiState(
    val localId: Long? = null,
    val item: String = "",
    val priceInput: String = "",
    val discountPercentInput: String = "",
    val finalPriceInput: String = "",
    val amountInput: String = "",
)
