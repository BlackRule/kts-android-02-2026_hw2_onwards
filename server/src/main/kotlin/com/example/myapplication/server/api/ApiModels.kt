package com.example.myapplication.server.api

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val message: String,
)

@Serializable
data class UserResponse(
    val id: Long,
    val fullName: String,
    val position: String,
)

@Serializable
data class UsersListResponse(
    val users: List<UserResponse>,
)

@Serializable
data class ShopResponse(
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

@Serializable
data class CreateShopRequest(
    val name: String,
    val city: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val address: String? = null,
)

@Serializable
data class ShopsListResponse(
    val shops: List<ShopResponse>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val hasNextPage: Boolean,
)

@Serializable
data class ShoppingListResponse(
    val id: Long,
    val shop: ShopResponse,
    val paidAt: String,
    val totalAmountMinor: Long,
)

@Serializable
data class ShoppingListsListResponse(
    val shoppingLists: List<ShoppingListResponse>,
)

@Serializable
data class UpsertShoppingListRequest(
    val shopId: Long,
    val paidAt: String,
    val totalAmountMinor: Long,
)

@Serializable
data class ErrorResponse(
    val message: String,
)
