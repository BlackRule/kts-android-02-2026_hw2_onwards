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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.myapplication.core.di.LocalAppContainer
import com.example.myapplication.core.location.findCurrentLocation
import com.example.myapplication.core.location.hasLocationPermission
import com.example.myapplication.core.location.locationPermissions
import com.example.myapplication.feature.shopPicker.model.ShopItem
import com.example.myapplication.feature.shopPicker.repository.ShopsRepository
import kotlinx.coroutines.launch

@Composable
actual fun ShopPickerScreen(
    initialQuery: String,
    onAddNewShop: () -> Unit,
    onShopSelected: ((ShopItem) -> Unit)?,
    modifier: Modifier,
) {
    val appContainer = LocalAppContainer.current
    val viewModel: ShopPickerViewModel = viewModel(
        factory = remember(appContainer.shopsRepository) {
            shopPickerViewModelFactory(appContainer.shopsRepository)
        },
    )
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

    SwipeRefreshContainer(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier,
    ) {
        ShopPickerContent(
            state = state,
            onSearchQueryChanged = viewModel::onSearchQueryChanged,
            onRetry = viewModel::retry,
            onLoadNextPage = viewModel::onLoadNextPage,
            locationBannerState = locationBannerState,
            isResolvingLocation = isResolvingLocation,
            onRequestLocation = ::requestLocationOrLoad,
            onAddNewShop = onAddNewShop,
            onShopSelected = onShopSelected,
            modifier = Modifier,
        )
    }
}

@Composable
actual fun SwipeRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val latestContent by rememberUpdatedState(content)
    val latestOnRefresh by rememberUpdatedState(onRefresh)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val composeView = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    latestContent()
                }
            }

            SwipeRefreshLayout(context).apply {
                setOnRefreshListener { latestOnRefresh() }
                addView(
                    composeView,
                    android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        },
        update = { swipeRefreshLayout ->
            swipeRefreshLayout.isRefreshing = isRefreshing
            swipeRefreshLayout.setOnRefreshListener { latestOnRefresh() }
        },
    )
}

private fun shopPickerViewModelFactory(
    shopsRepository: ShopsRepository,
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ShopPickerViewModel(
                shopsRepository = shopsRepository,
            ) as T
        }
    }
}
