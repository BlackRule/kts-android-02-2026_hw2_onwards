package com.example.myapplication.feature.itemCatalog.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface CameraPermissionState {
    val isGranted: Boolean

    fun requestPermission()
}

@Composable
expect fun rememberCameraPermissionState(): CameraPermissionState

@Composable
expect fun BarcodeScannerPreview(
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
)
