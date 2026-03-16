package com.example.myapplication.feature.shopCreation.presentation

data class CreateShopUiState(
    val name: String = "",
    val city: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val createdShopName: String? = null,
)
