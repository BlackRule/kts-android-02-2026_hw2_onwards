package com.example.myapplication.core.network

import io.ktor.client.engine.HttpClientEngineFactory

internal expect fun httpClientEngineFactory(): HttpClientEngineFactory<*>
