package com.example.myapplication.feature.shopCreation.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.core.location.GeoPoint
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.mapview.MapView
import java.util.Locale

@Composable
actual fun PlatformMapPicker(
    selectedPoint: GeoPoint?,
    cameraPoint: GeoPoint?,
    onPointSelected: (GeoPoint) -> Unit,
    modifier: Modifier,
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

actual fun formatCoordinate(value: Double): String {
    return String.format(Locale.US, "%.6f", value)
}
