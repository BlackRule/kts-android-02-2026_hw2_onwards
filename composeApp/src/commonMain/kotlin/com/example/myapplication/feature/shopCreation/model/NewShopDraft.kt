package com.example.myapplication.feature.shopCreation.model

data class NewShopDraft(
    val name: String,
    val city: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
