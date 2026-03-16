package com.example.myapplication.feature.shopPicker.presentation

import com.example.myapplication.feature.shopPicker.model.ShopItem

data class ShopPickerUiState(
    val query: String = "",
    val shops: List<ShopItem> = emptyList(),
    val closestShopId: Long? = null,
    val isLoading: Boolean = true,
    val isLoadingNextPage: Boolean = false,
    val errorMessage: String? = null,
    val paginationError: String? = null,
    val currentPage: Int = 0,
    val hasNextPage: Boolean = false,
)
