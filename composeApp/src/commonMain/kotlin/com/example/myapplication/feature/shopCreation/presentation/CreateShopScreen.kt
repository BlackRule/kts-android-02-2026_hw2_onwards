package com.example.myapplication.feature.shopCreation.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun CreateShopScreen(
    onBack: () -> Unit,
    onShopCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
)
