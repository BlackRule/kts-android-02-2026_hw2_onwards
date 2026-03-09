package com.example.myapplication.feature.shopPicker.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.core.location.findCurrentLocation
import com.example.myapplication.core.location.hasLocationPermission
import com.example.myapplication.core.location.locationPermissions
import kotlinx.coroutines.launch

@Composable
actual fun ShopPickerScreen(
    initialQuery: String,
    onAddNewShop: () -> Unit,
    modifier: Modifier,
) {
    val viewModel: ShopPickerViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var hasRequestedLocationPermission by rememberSaveable { mutableStateOf(false) }
    var isResolvingLocation by remember { mutableStateOf(false) }
    var locationBannerState by remember {
        mutableStateOf(
            if (context.hasLocationPermission()) {
                ShopPickerLocationBannerState.Hidden
            } else {
                ShopPickerLocationBannerState.PermissionRequired
            },
        )
    }

    fun resolveCurrentLocation() {
        isResolvingLocation = true
        locationBannerState = ShopPickerLocationBannerState.Hidden

        coroutineScope.launch {
            val currentLocation = context.findCurrentLocation()
            isResolvingLocation = false

            if (currentLocation != null) {
                viewModel.onUserLocationChanged(currentLocation)
                locationBannerState = ShopPickerLocationBannerState.Hidden
            } else {
                locationBannerState = ShopPickerLocationBannerState.LocationUnavailable
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (context.hasLocationPermission()) {
            resolveCurrentLocation()
        } else {
            isResolvingLocation = false
            locationBannerState = ShopPickerLocationBannerState.PermissionRequired
        }
    }

    fun requestLocationOrLoad() {
        if (context.hasLocationPermission()) {
            resolveCurrentLocation()
        } else {
            permissionLauncher.launch(locationPermissions)
        }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank() && state.query != initialQuery) {
            viewModel.onSearchQueryChanged(initialQuery)
            viewModel.retry()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasRequestedLocationPermission) {
            hasRequestedLocationPermission = true
            requestLocationOrLoad()
        }
    }

    ShopPickerContent(
        state = state,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onRetry = viewModel::retry,
        onLoadNextPage = viewModel::onLoadNextPage,
        locationBannerState = locationBannerState,
        isResolvingLocation = isResolvingLocation,
        onRequestLocation = ::requestLocationOrLoad,
        onAddNewShop = onAddNewShop,
        modifier = modifier,
    )
}
