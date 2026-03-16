package com.example.myapplication.common.ui

import androidx.compose.runtime.Composable

@Composable
expect fun RefreshOnResumeEffect(onResume: () -> Unit)
