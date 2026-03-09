package com.example.myapplication.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

val locationPermissions: Array<String> = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

data class ResolvedAddress(
    val latitude: Double,
    val longitude: Double,
    val city: String?,
    val address: String?,
)

fun Context.hasLocationPermission(): Boolean {
    return locationPermissions.any { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}

suspend fun Context.findCurrentLocation(): GeoPoint? {
    if (!hasLocationPermission()) {
        return null
    }

    val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = buildList {
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            add(LocationManager.GPS_PROVIDER)
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            add(LocationManager.NETWORK_PROVIDER)
        }
        if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            add(LocationManager.PASSIVE_PROVIDER)
        }
    }

    providers.forEach { provider ->
        currentLocationFromProvider(locationManager, provider)?.let { return it }
    }

    return providers
        .asSequence()
        .mapNotNull { provider -> locationManager.getLastKnownLocation(provider)?.toGeoPoint() }
        .firstOrNull()
}

suspend fun Context.reverseGeocode(point: GeoPoint): ResolvedAddress? {
    return geocode {
        getFromLocation(point.latitude, point.longitude, 1)
            ?.firstOrNull()
            ?.toResolvedAddress()
    }
}

suspend fun Context.searchAddress(query: String): ResolvedAddress? {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) {
        return null
    }

    return geocode {
        getFromLocationName(normalizedQuery, 1)
            ?.firstOrNull()
            ?.toResolvedAddress()
    }
}

private suspend fun Context.currentLocationFromProvider(
    locationManager: LocationManager,
    provider: String,
): GeoPoint? {
    return suspendCancellableCoroutine { continuation ->
        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }

        try {
            LocationManagerCompat.getCurrentLocation(
                locationManager,
                provider,
                cancellationSignal,
                ContextCompat.getMainExecutor(this),
            ) { location ->
                continuation.resume(location?.toGeoPoint())
            }
        } catch (_: SecurityException) {
            continuation.resume(null)
        } catch (_: IllegalArgumentException) {
            continuation.resume(null)
        }
    }
}

private suspend fun Context.geocode(
    resolve: Geocoder.() -> ResolvedAddress?,
): ResolvedAddress? {
    if (!Geocoder.isPresent()) {
        return null
    }

    return withContext(Dispatchers.IO) {
        runCatching {
            Geocoder(this@geocode, Locale.getDefault()).resolve()
        }.getOrNull()
    }
}

private fun Location.toGeoPoint(): GeoPoint {
    return GeoPoint(
        latitude = latitude,
        longitude = longitude,
    )
}

@Suppress("DEPRECATION")
private fun Address.toResolvedAddress(): ResolvedAddress {
    return ResolvedAddress(
        latitude = latitude,
        longitude = longitude,
        city = locality
            ?: subAdminArea
            ?: adminArea,
        address = buildAddressLine(),
    )
}

private fun Address.buildAddressLine(): String? {
    val firstLine = getAddressLine(0)?.trim()
    if (!firstLine.isNullOrEmpty()) {
        return firstLine
    }

    return listOfNotNull(
        thoroughfare?.trim()?.takeIf(String::isNotEmpty),
        subThoroughfare?.trim()?.takeIf(String::isNotEmpty),
        locality?.trim()?.takeIf(String::isNotEmpty),
    ).takeIf { it.isNotEmpty() }
        ?.joinToString(", ")
}
