package com.example.myapplication.core.logging

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

internal actual fun initializePlatformLogger() {
    Napier.base(DebugAntilog())
}
