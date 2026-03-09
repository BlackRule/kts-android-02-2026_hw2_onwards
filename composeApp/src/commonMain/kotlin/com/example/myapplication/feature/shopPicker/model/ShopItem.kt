package com.example.myapplication.feature.shopPicker.model

data class ShopItem(
    val id: Long,
    val name: String,
    val city: String? = null,
    val openingTime: String,
    val closingTime: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val address: String? = null,
    val enabled: Boolean,
)

data class ShopsPage(
    val shops: List<ShopItem>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val hasNextPage: Boolean,
)
