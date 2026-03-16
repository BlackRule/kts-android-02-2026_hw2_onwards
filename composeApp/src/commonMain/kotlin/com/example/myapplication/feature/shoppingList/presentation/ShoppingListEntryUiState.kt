package com.example.myapplication.feature.shoppingList.presentation

import com.example.myapplication.feature.itemCatalog.model.UnitType

data class ShoppingListEntryUiState(
    val localId: Long? = null,
    val itemBarcode: String? = null,
    val itemMainName: String = "",
    val pendingItemId: Long? = null,
    val unit: UnitType? = null,
    val priceInput: String = "",
    val discountPercentInput: String = "",
    val finalPriceInput: String = "",
    val amountInput: String = "",
    val totalDisplay: String = "",
)
