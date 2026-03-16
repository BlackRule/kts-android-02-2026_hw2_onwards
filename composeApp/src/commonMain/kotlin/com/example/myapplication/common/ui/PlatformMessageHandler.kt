package com.example.myapplication.common.ui

import androidx.compose.runtime.Composable

@Composable
expect fun rememberPlatformMessageHandler(): (String) -> Unit
