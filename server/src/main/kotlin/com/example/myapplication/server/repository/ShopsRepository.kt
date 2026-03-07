package com.example.myapplication.server.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ShopEntity(
    val id: Long,
    val name: String,
    val city: String,
    val openingTime: String,
    val closingTime: String,
    val lat: Double,
    val lon: Double,
    val address: String,
    val enabled: Boolean,
)

data class ShopsPageEntity(
    val shops: List<ShopEntity>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val hasNextPage: Boolean,
)

interface ShopsRepository {
    fun getList(
        query: String,
        page: Int,
        pageSize: Int,
    ): Result<ShopsPageEntity>
}

class JsonShopsRepository(
    private val fileName: String = "stores.json",
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ShopsRepository {

    private val allShops: List<ShopEntity> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        loadShops()
    }

    override fun getList(
        query: String,
        page: Int,
        pageSize: Int,
    ): Result<ShopsPageEntity> {
        return runCatching {
            val normalizedQuery = query.trim()
            val safePage = page.coerceAtLeast(1)
            val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
            val filtered = if (normalizedQuery.isBlank()) {
                allShops
            } else {
                allShops.filter { shop ->
                    shop.name.contains(normalizedQuery, ignoreCase = true) ||
                        shop.city.contains(normalizedQuery, ignoreCase = true) ||
                        shop.address.contains(normalizedQuery, ignoreCase = true)
                }
            }

            val fromIndex = ((safePage - 1) * safePageSize).coerceAtMost(filtered.size)
            val toIndex = (fromIndex + safePageSize).coerceAtMost(filtered.size)
            val pageItems = if (fromIndex >= toIndex) emptyList() else filtered.subList(fromIndex, toIndex)

            ShopsPageEntity(
                shops = pageItems,
                page = safePage,
                pageSize = safePageSize,
                totalCount = filtered.size,
                hasNextPage = toIndex < filtered.size,
            )
        }
    }

    private fun loadShops(): List<ShopEntity> {
        val resourceStream = javaClass.classLoader.getResourceAsStream(fileName)
            ?: error("Missing resource: $fileName")

        return resourceStream.bufferedReader().use { reader ->
            json.decodeFromString<List<ShopEntity>>(reader.readText())
        }
    }

    private companion object {
        private const val MAX_PAGE_SIZE = 50
    }
}
