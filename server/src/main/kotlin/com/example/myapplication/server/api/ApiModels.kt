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
    val city: String,
    val openingTime: String,
    val closingTime: String,
    val lat: Double,
    val lon: Double,
    val address: String,
    val enabled: Boolean,
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
data class ErrorResponse(
    val message: String,
)
