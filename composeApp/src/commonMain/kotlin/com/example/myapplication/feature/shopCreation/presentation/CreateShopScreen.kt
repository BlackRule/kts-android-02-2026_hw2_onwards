package com.example.myapplication.feature.shopCreation.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.common.ui.asString
import com.example.myapplication.common.ui.theme.Dimens
import com.example.myapplication.core.location.GeoPoint
import com.example.myapplication.core.location.ResolvedAddress
import com.example.myapplication.core.location.platformHasMapPicker
import com.example.myapplication.core.location.rememberLocationPermissionState
import com.example.myapplication.core.location.rememberLocationService
import kotlinx.coroutines.launch
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.create_shop_address_label
import myapplication.composeapp.generated.resources.create_shop_address_lookup_error
import myapplication.composeapp.generated.resources.create_shop_address_search_label
import myapplication.composeapp.generated.resources.create_shop_address_search_placeholder
import myapplication.composeapp.generated.resources.create_shop_back_button
import myapplication.composeapp.generated.resources.create_shop_city_label
import myapplication.composeapp.generated.resources.create_shop_clear_location_button
import myapplication.composeapp.generated.resources.create_shop_current_location_error
import myapplication.composeapp.generated.resources.create_shop_latitude_label
import myapplication.composeapp.generated.resources.create_shop_location_permission_message
import myapplication.composeapp.generated.resources.create_shop_longitude_label
import myapplication.composeapp.generated.resources.create_shop_map_hint
import myapplication.composeapp.generated.resources.create_shop_map_missing_key
import myapplication.composeapp.generated.resources.create_shop_name_label
import myapplication.composeapp.generated.resources.create_shop_name_placeholder
import myapplication.composeapp.generated.resources.create_shop_save_button
import myapplication.composeapp.generated.resources.create_shop_saving_button
import myapplication.composeapp.generated.resources.create_shop_search_address_button
import myapplication.composeapp.generated.resources.create_shop_title
import myapplication.composeapp.generated.resources.create_shop_use_current_location_button
import org.jetbrains.compose.resources.stringResource

@Composable
fun CreateShopScreen(
    returnToSelectionMode: Boolean,
    onBack: () -> Unit,
    onShopCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CreateShopViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val locationPermissionState = rememberLocationPermissionState()
    val locationService = rememberLocationService()

    val addressLookupErrorText = stringResource(Res.string.create_shop_address_lookup_error)
    val currentLocationErrorText = stringResource(Res.string.create_shop_current_location_error)

    var addressSearchQuery by rememberSaveable { mutableStateOf("") }
    var lookupErrorMessage by remember { mutableStateOf<String?>(null) }
    var hasRequestedLocationPermission by rememberSaveable { mutableStateOf(false) }
    var isResolvingCurrentLocation by remember { mutableStateOf(false) }
    var isSearchingAddress by remember { mutableStateOf(false) }
    var isResolvingMapTap by remember { mutableStateOf(false) }
    var mapCameraPoint by remember { mutableStateOf<GeoPoint?>(null) }

    fun requestLocationOrUseGps() {
        if (locationPermissionState.hasPermission) {
            resolveFromCurrentLocation(
                coroutineScope = coroutineScope,
                locationService = locationService,
                onStart = {
                    isResolvingCurrentLocation = true
                    lookupErrorMessage = null
                },
                onResolved = { resolvedAddress ->
                    applyResolvedAddress(
                        resolvedAddress = resolvedAddress,
                        onApply = viewModel::onLocationResolved,
                        onAddressQueryChanged = { addressSearchQuery = it },
                        onCameraPointChanged = { mapCameraPoint = it },
                    )
                },
                onFailed = {
                    lookupErrorMessage = currentLocationErrorText
                },
                onFinished = { isResolvingCurrentLocation = false },
            )
        } else {
            locationPermissionState.requestPermission()
        }
    }

    fun searchByAddress() {
        val query = addressSearchQuery.trim()
        if (query.isEmpty()) {
            lookupErrorMessage = addressLookupErrorText
            return
        }

        isSearchingAddress = true
        lookupErrorMessage = null

        coroutineScope.launch {
            val resolvedAddress = locationService.searchAddress(query)
            if (resolvedAddress != null) {
                applyResolvedAddress(
                    resolvedAddress = resolvedAddress,
                    onApply = viewModel::onLocationResolved,
                    onAddressQueryChanged = { addressSearchQuery = it },
                    onCameraPointChanged = { mapCameraPoint = it },
                )
            } else {
                lookupErrorMessage = addressLookupErrorText
            }
            isSearchingAddress = false
        }
    }

    fun resolveMapTap(point: GeoPoint) {
        isResolvingMapTap = true
        lookupErrorMessage = null

        coroutineScope.launch {
            val resolvedAddress = locationService.reverseGeocode(point)
            if (resolvedAddress != null) {
                applyResolvedAddress(
                    resolvedAddress = resolvedAddress,
                    onApply = viewModel::onLocationResolved,
                    onAddressQueryChanged = { addressSearchQuery = it },
                    onCameraPointChanged = { mapCameraPoint = it },
                )
            } else {
                lookupErrorMessage = addressLookupErrorText
            }
            isResolvingMapTap = false
        }
    }

    LaunchedEffect(locationPermissionState.hasPermission) {
        if (locationPermissionState.hasPermission && hasRequestedLocationPermission) {
            requestLocationOrUseGps()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasRequestedLocationPermission) {
            hasRequestedLocationPermission = true
            requestLocationOrUseGps()
        }
    }

    LaunchedEffect(state.createdShopName) {
        val createdShopName = state.createdShopName ?: return@LaunchedEffect
        viewModel.consumeCreatedShop()
        onShopCreated(createdShopName)
    }

    val selectedPoint = remember(state.latitude, state.longitude) {
        val latitude = state.latitude
        val longitude = state.longitude
        if (latitude != null && longitude != null) {
            GeoPoint(
                latitude = latitude,
                longitude = longitude,
            )
        } else {
            null
        }
    }

    Column(
        modifier = modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(text = stringResource(Res.string.create_shop_back_button))
        }

        Text(
            text = stringResource(Res.string.create_shop_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChanged,
            label = { Text(text = stringResource(Res.string.create_shop_name_label)) },
            placeholder = { Text(text = stringResource(Res.string.create_shop_name_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = addressSearchQuery,
            onValueChange = {
                addressSearchQuery = it
                lookupErrorMessage = null
            },
            label = { Text(text = stringResource(Res.string.create_shop_address_search_label)) },
            placeholder = { Text(text = stringResource(Res.string.create_shop_address_search_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = ::searchByAddress,
                enabled = !isSearchingAddress && !state.isSaving,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(Res.string.create_shop_search_address_button))
            }
            Button(
                onClick = ::requestLocationOrUseGps,
                enabled = !isResolvingCurrentLocation && !state.isSaving,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(Res.string.create_shop_use_current_location_button))
            }
        }

        if (lookupErrorMessage != null || state.errorMessage != null) {
            Text(
                text = lookupErrorMessage ?: state.errorMessage?.asString().orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (isResolvingCurrentLocation || isSearchingAddress || isResolvingMapTap) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        if (!locationPermissionState.hasPermission) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.create_shop_location_permission_message),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (!platformHasMapPicker()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.create_shop_map_missing_key),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Text(
                text = stringResource(Res.string.create_shop_map_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlatformMapPicker(
                selectedPoint = selectedPoint,
                cameraPoint = mapCameraPoint ?: selectedPoint,
                onPointSelected = ::resolveMapTap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
            )
        }

        OutlinedTextField(
            value = state.city,
            onValueChange = {},
            readOnly = true,
            label = { Text(text = stringResource(Res.string.create_shop_city_label)) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.address,
            onValueChange = {},
            readOnly = true,
            label = { Text(text = stringResource(Res.string.create_shop_address_label)) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.latitude?.let(::formatCoordinate).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(text = stringResource(Res.string.create_shop_latitude_label)) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.longitude?.let(::formatCoordinate).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(text = stringResource(Res.string.create_shop_longitude_label)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = {
                    viewModel.clearResolvedLocation()
                    addressSearchQuery = ""
                    mapCameraPoint = null
                    lookupErrorMessage = null
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(Res.string.create_shop_clear_location_button))
            }

            Button(
                onClick = viewModel::saveShop,
                enabled = !state.isSaving,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (state.isSaving) {
                        stringResource(Res.string.create_shop_saving_button)
                    } else {
                        stringResource(Res.string.create_shop_save_button)
                    },
                )
            }
        }
    }
}

private fun resolveFromCurrentLocation(
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    locationService: com.example.myapplication.core.location.LocationService,
    onStart: () -> Unit,
    onResolved: (ResolvedAddress) -> Unit,
    onFailed: () -> Unit,
    onFinished: () -> Unit,
) {
    onStart()
    coroutineScope.launch {
        val currentLocation = locationService.findCurrentLocation()
        val resolvedAddress = if (currentLocation != null) {
            locationService.reverseGeocode(currentLocation)
        } else {
            null
        }
        if (resolvedAddress != null) {
            onResolved(resolvedAddress)
        } else {
            onFailed()
        }
        onFinished()
    }
}

private fun applyResolvedAddress(
    resolvedAddress: ResolvedAddress,
    onApply: (Double, Double, String?, String?) -> Unit,
    onAddressQueryChanged: (String) -> Unit,
    onCameraPointChanged: (GeoPoint) -> Unit,
) {
    onApply(
        resolvedAddress.latitude,
        resolvedAddress.longitude,
        resolvedAddress.city,
        resolvedAddress.address,
    )
    onAddressQueryChanged(resolvedAddress.address.orEmpty())
    onCameraPointChanged(
        GeoPoint(
            latitude = resolvedAddress.latitude,
            longitude = resolvedAddress.longitude,
        ),
    )
}
