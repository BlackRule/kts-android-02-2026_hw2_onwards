package com.example.myapplication.server.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

@Serializable
data class ShopEntity(
    val id: Long,
    val name: String,
    val city: String? = null,
    val openingTime: String,
    val closingTime: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val address: String? = null,
    val enabled: Boolean,
)

data class CreateShopEntity(
    val name: String,
    val city: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val address: String? = null,
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

    fun createShop(shop: CreateShopEntity): Result<ShopEntity>
}

class JsonShopsRepository(
    private val storagePath: String = defaultShopsStoragePath(),
    private val seedResourceName: String = "stores.json",
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
    },
) : ShopsRepository {

    private val storageFile: Path = Paths.get(storagePath)
    private val lock = Any()
    private var cachedShops: MutableList<ShopEntity>? = null

    override fun getList(
        query: String,
        page: Int,
        pageSize: Int,
    ): Result<ShopsPageEntity> {
        return runCatching {
            val allShops = loadShopsSnapshot()
            val normalizedQuery = query.trim()
            val safePage = page.coerceAtLeast(1)
            val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
            val filtered = if (normalizedQuery.isBlank()) {
                allShops
            } else {
                allShops.filter { shop ->
                    shop.name.contains(normalizedQuery, ignoreCase = true) ||
                        shop.city.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
                        shop.address.orEmpty().contains(normalizedQuery, ignoreCase = true)
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

    override fun createShop(shop: CreateShopEntity): Result<ShopEntity> {
        return runCatching {
            val normalizedName = shop.name.trim()
            require(normalizedName.isNotEmpty()) {
                "Shop name is required"
            }

            synchronized(lock) {
                val shops = loadShopsLocked()
                val createdShop = ShopEntity(
                    id = (shops.maxOfOrNull { it.id } ?: 0L) + 1L,
                    name = normalizedName,
                    city = shop.city.normalizedOrNull(),
                    openingTime = DEFAULT_OPENING_TIME,
                    closingTime = DEFAULT_CLOSING_TIME,
                    lat = shop.lat,
                    lon = shop.lon,
                    address = shop.address.normalizedOrNull(),
                    enabled = true,
                )
                shops.add(0, createdShop)
                persistShopsLocked(shops)
                createdShop
            }
        }
    }

    private fun loadShopsSnapshot(): List<ShopEntity> {
        return synchronized(lock) {
            loadShopsLocked().toList()
        }
    }

    private fun loadShopsLocked(): MutableList<ShopEntity> {
        cachedShops?.let { return it }

        ensureStorageFileExists()
        val loadedShops = Files.readString(storageFile, StandardCharsets.UTF_8)
            .let { json.decodeFromString<List<ShopEntity>>(it) }
            .toMutableList()
        cachedShops = loadedShops
        return loadedShops
    }

    private fun persistShopsLocked(shops: List<ShopEntity>) {
        storageFile.parent?.let(Files::createDirectories)
        Files.writeString(
            storageFile,
            json.encodeToString(shops),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    private fun ensureStorageFileExists() {
        if (Files.exists(storageFile)) {
            return
        }

        storageFile.parent?.let(Files::createDirectories)
        Files.writeString(
            storageFile,
            readSeedShops(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        )
    }

    private fun readSeedShops(): String {
        val resourceStream = javaClass.classLoader.getResourceAsStream(seedResourceName)
            ?: error("Missing resource: $seedResourceName")

        return resourceStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            reader.readText()
        }
    }

    private companion object {
        private const val MAX_PAGE_SIZE = 50
        private const val DEFAULT_OPENING_TIME = "08:00"
        private const val DEFAULT_CLOSING_TIME = "23:00"

        private fun defaultShopsStoragePath(): String {
            return System.getenv("SHOPS_FILE_PATH")
                ?.takeIf(String::isNotBlank)
                ?: Paths.get(System.getProperty("user.dir"), "data", "stores.json").toString()
        }
    }
}

private fun String?.normalizedOrNull(): String? {
    return this?.trim()?.takeIf(String::isNotEmpty)
}
