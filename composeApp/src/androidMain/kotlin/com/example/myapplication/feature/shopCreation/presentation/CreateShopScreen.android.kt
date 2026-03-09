package com.example.myapplication.feature.shopCreation.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.BuildConfig
import com.example.myapplication.common.ui.theme.Dimens
import com.example.myapplication.core.location.GeoPoint
import com.example.myapplication.core.location.ResolvedAddress
import com.example.myapplication.core.location.findCurrentLocation
import com.example.myapplication.core.location.hasLocationPermission
import com.example.myapplication.core.location.locationPermissions
import com.example.myapplication.core.location.reverseGeocode
import com.example.myapplication.core.location.searchAddress
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.mapview.MapView
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
import java.util.Locale

@Composable
actual fun CreateShopScreen(
    returnToSelectionMode: Boolean,
    onBack: () -> Unit,
    onShopCreated: (String) -> Unit,
    modifier: Modifier,
) {
    val viewModel: CreateShopViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val addressLookupErrorText = stringResource(Res.string.create_shop_address_lookup_error)
    val currentLocationErrorText = stringResource(Res.string.create_shop_current_location_error)

    var addressSearchQuery by rememberSaveable { mutableStateOf("") }
    var lookupErrorMessage by remember { mutableStateOf<String?>(null) }
    var hasRequestedLocationPermission by rememberSaveable { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var isResolvingCurrentLocation by remember { mutableStateOf(false) }
    var isSearchingAddress by remember { mutableStateOf(false) }
    var isResolvingMapTap by remember { mutableStateOf(false) }
    var mapCameraPoint by remember { mutableStateOf<GeoPoint?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasLocationPermission = context.hasLocationPermission()
        if (hasLocationPermission) {
            resolveFromCurrentLocation(
                context = context,
                coroutineScope = coroutineScope,
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
        }
    }

    fun requestLocationOrUseGps() {
        hasLocationPermission = context.hasLocationPermission()
        if (hasLocationPermission) {
            resolveFromCurrentLocation(
                context = context,
                coroutineScope = coroutineScope,
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
            permissionLauncher.launch(locationPermissions)
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
            val resolvedAddress = context.searchAddress(query)
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
            val resolvedAddress = context.reverseGeocode(point)
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
                text = lookupErrorMessage ?: state.errorMessage.orEmpty(),
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

        if (!hasLocationPermission) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.create_shop_location_permission_message),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (BuildConfig.MAPKIT_API_KEY.isBlank()) {
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
            YandexMapPicker(
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
            value = state.latitude?.formatCoordinate().orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(text = stringResource(Res.string.create_shop_latitude_label)) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.longitude?.formatCoordinate().orEmpty(),
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

@Composable
private fun YandexMapPicker(
    selectedPoint: GeoPoint?,
    cameraPoint: GeoPoint?,
    onPointSelected: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapKitFactory.initialize(context.applicationContext)
        MapView(context)
    }
    val placemarkCollection = remember(mapView) {
        mapView.mapWindow.map.mapObjects.addCollection()
    }
    val inputListener = remember(onPointSelected) {
        object : InputListener {
            override fun onMapTap(map: Map, point: Point) {
                onPointSelected(
                    GeoPoint(
                        latitude = point.latitude,
                        longitude = point.longitude,
                    ),
                )
            }

            override fun onMapLongTap(map: Map, point: Point) = Unit
        }
    }

    DisposableEffect(mapView, inputListener) {
        mapView.mapWindow.map.addInputListener(inputListener)
        onDispose {
            mapView.mapWindow.map.removeInputListener(inputListener)
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    MapKitFactory.getInstance().onStart()
                    mapView.onStart()
                }

                Lifecycle.Event.ON_STOP -> {
                    mapView.onStop()
                    MapKitFactory.getInstance().onStop()
                }

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            MapKitFactory.getInstance().onStart()
            mapView.onStart()
        }
        onDispose {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                mapView.onStop()
                MapKitFactory.getInstance().onStop()
            }
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            placemarkCollection.clear()
            selectedPoint?.let { point ->
                placemarkCollection.addPlacemark(
                    Point(point.latitude, point.longitude),
                )
            }

            val targetPoint = selectedPoint ?: cameraPoint
            targetPoint?.let { point ->
                view.mapWindow.map.move(
                    CameraPosition(
                        Point(point.latitude, point.longitude),
                        if (selectedPoint != null) 16f else 14f,
                        0f,
                        0f,
                    ),
                    Animation(Animation.Type.SMOOTH, 0.25f),
                    null,
                )
            }
        },
    )
}

private fun resolveFromCurrentLocation(
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onStart: () -> Unit,
    onResolved: (ResolvedAddress) -> Unit,
    onFailed: () -> Unit,
    onFinished: () -> Unit,
) {
    onStart()
    coroutineScope.launch {
        val currentLocation = context.findCurrentLocation()
        val resolvedAddress = if (currentLocation != null) {
            context.reverseGeocode(currentLocation)
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

private fun Double.formatCoordinate(): String {
    return String.format(Locale.US, "%.6f", this)
}
