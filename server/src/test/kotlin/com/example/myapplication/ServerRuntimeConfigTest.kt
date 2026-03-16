package com.example.myapplication

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ServerRuntimeConfigTest {

    @Test
    fun defaultsToPlainHttpWhenTlsEnvironmentIsMissing() {
        val config = serverRuntimeConfigFromEnvironment(emptyMap())

        assertEquals("0.0.0.0", config.host)
        assertEquals(DEFAULT_SERVER_PORT, config.port)
        assertNull(config.tls)
    }

    @Test
    fun parsesCompleteTlsEnvironment() {
        val config = serverRuntimeConfigFromEnvironment(
            mapOf(
                "SERVER_HOST" to "195.46.171.236",
                "SERVER_PORT" to "9878",
                "SSL_KEYSTORE_PATH" to "/tmp/server-keystore.p12",
                "SSL_KEYSTORE_PASSWORD" to "changeit",
                "SSL_PRIVATE_KEY_PASSWORD" to "changeit",
                "SSL_KEY_ALIAS" to "myapplication",
                "SSL_KEYSTORE_TYPE" to "PKCS12",
            ),
        )

        assertEquals("195.46.171.236", config.host)
        assertEquals(9878, config.port)
        assertEquals("/tmp/server-keystore.p12", config.tls?.keyStorePath)
        assertEquals("changeit", config.tls?.keyStorePassword)
        assertEquals("changeit", config.tls?.privateKeyPassword)
        assertEquals("myapplication", config.tls?.keyAlias)
        assertEquals("PKCS12", config.tls?.keyStoreType)
    }

    @Test
    fun rejectsPartialTlsEnvironment() {
        val error = assertFailsWith<IllegalArgumentException> {
            serverRuntimeConfigFromEnvironment(
                mapOf(
                    "SSL_KEYSTORE_PATH" to "/tmp/server-keystore.p12",
                ),
            )
        }

        assertContains(error.message.orEmpty(), "SSL_KEYSTORE_PASSWORD")
        assertContains(error.message.orEmpty(), "SSL_PRIVATE_KEY_PASSWORD")
        assertContains(error.message.orEmpty(), "SSL_KEY_ALIAS")
    }
}
