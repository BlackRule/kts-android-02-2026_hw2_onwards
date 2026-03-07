package com.example.myapplication.feature.shopPicker.model

data class ShopItem(
    val id: Long,
    val name: String,
    val city: String,
    val openingTime: String,
    val closingTime: String,
    val lat: Double,
    val lon: Double,
    val address: String,
    val enabled: Boolean,
)

data class ShopsPage(
    val shops: List<ShopItem>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val hasNextPage: Boolean,
)
