package com.example.myapplication.feature.itemCatalog.presentation

import android.os.SystemClock

actual fun platformElapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
