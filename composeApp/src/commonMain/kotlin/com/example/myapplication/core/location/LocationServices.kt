package com.example.myapplication.core.location

import androidx.compose.runtime.Composable

data class ResolvedAddress(
    val latitude: Double,
    val longitude: Double,
    val city: String?,
    val address: String?,
)

interface LocationPermissionState {
    val hasPermission: Boolean

    fun requestPermission()
}

interface LocationService {
    suspend fun findCurrentLocation(): GeoPoint?

    suspend fun reverseGeocode(point: GeoPoint): ResolvedAddress?

    suspend fun searchAddress(query: String): ResolvedAddress?
}

@Composable
expect fun rememberLocationPermissionState(): LocationPermissionState

@Composable
expect fun rememberLocationService(): LocationService

expect fun platformHasMapPicker(): Boolean
