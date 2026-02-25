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
data class ErrorResponse(
    val message: String,
)
