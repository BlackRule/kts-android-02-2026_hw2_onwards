package com.example.myapplication.feature.profile.model

import kotlinx.serialization.Serializable

@Serializable
data class LoggedInProfile(
    val id: Long,
    val username: String,
    val fullName: String,
    val position: String,
)
