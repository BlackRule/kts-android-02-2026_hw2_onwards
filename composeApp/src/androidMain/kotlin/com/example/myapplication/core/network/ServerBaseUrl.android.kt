package com.example.myapplication.core.network

import com.example.myapplication.BuildConfig
import java.net.URI

private val disallowedHosts = setOf("localhost", "127.0.0.1", "0.0.0.0")

internal actual val serverBaseUrl: String = resolveServerBaseUrl(BuildConfig.SERVER_BASE_URL)

private fun resolveServerBaseUrl(configuredValue: String): String {
    val trimmed = configuredValue.trim().removeSuffix("/")
    val uri = runCatching { URI(trimmed) }.getOrNull()
    val scheme = uri?.scheme?.lowercase()
    val host = uri?.host?.lowercase()

    return if (
        trimmed.isNotEmpty() &&
        scheme == "https" &&
        host != null &&
        host !in disallowedHosts
    ) {
        trimmed
    } else {
        DEFAULT_SERVER_BASE_URL
    }
}

private const val DEFAULT_SERVER_BASE_URL = "https://195.46.171.236:9878"
