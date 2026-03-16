package com.example.myapplication.core.logging

object AppLogger {
    private var initialized = false

    fun initialize() {
        if (initialized) {
            return
        }
        initialized = true
        initializePlatformLogger()
    }
}

internal expect fun initializePlatformLogger()
