package com.example.myapplication.feature.shopCreation.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.myapplication.core.location.GeoPoint

@Composable
expect fun PlatformMapPicker(
    selectedPoint: GeoPoint?,
    cameraPoint: GeoPoint?,
    onPointSelected: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
)

expect fun formatCoordinate(value: Double): String
