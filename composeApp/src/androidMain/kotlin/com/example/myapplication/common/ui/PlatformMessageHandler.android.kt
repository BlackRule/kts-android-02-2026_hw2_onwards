package com.example.myapplication.common.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPlatformMessageHandler(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
