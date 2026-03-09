package com.example.myapplication.server.repository

import com.example.myapplication.server.data.DatabaseFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

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

    fun getById(id: Long): Result<ShopEntity?>

    fun createShop(shop: CreateShopEntity): Result<ShopEntity>
}

class PostgresShopsRepository : ShopsRepository {

    override fun getList(
        query: String,
        page: Int,
        pageSize: Int,
    ): Result<ShopsPageEntity> {
        return runCatching {
            val normalizedQuery = query.trim()
            val safePage = page.coerceAtLeast(1)
            val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
            val offset = (safePage - 1) * safePageSize

            DatabaseFactory.getConnection().use { connection ->
                val totalCount = connection.prepareStatement(
                    """
                    SELECT COUNT(*)
                    FROM shops
                    WHERE (? = '' OR name ILIKE ? OR COALESCE(city, '') ILIKE ? OR COALESCE(address, '') ILIKE ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setSearchParameters(normalizedQuery)
                    statement.executeQuery().use { resultSet ->
                        resultSet.next()
                        resultSet.getInt(1)
                    }
                }

                val shops = connection.prepareStatement(
                    """
                    SELECT id, name, city, opening_time, closing_time, lat, lon, address, enabled
                    FROM shops
                    WHERE (? = '' OR name ILIKE ? OR COALESCE(city, '') ILIKE ? OR COALESCE(address, '') ILIKE ?)
                    ORDER BY sort_order ASC, id DESC
                    LIMIT ? OFFSET ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setSearchParameters(normalizedQuery)
                    statement.setInt(5, safePageSize)
                    statement.setInt(6, offset)
                    statement.executeQuery().use { resultSet ->
                        buildList {
                            while (resultSet.next()) {
                                add(resultSet.toShopEntity())
                            }
                        }
                    }
                }

                ShopsPageEntity(
                    shops = shops,
                    page = safePage,
                    pageSize = safePageSize,
                    totalCount = totalCount,
                    hasNextPage = offset + shops.size < totalCount,
                )
            }
        }
    }

    override fun getById(id: Long): Result<ShopEntity?> {
        return runCatching {
            DatabaseFactory.getConnection().use { connection ->
                connection.prepareStatement(
                    """
                    SELECT id, name, city, opening_time, closing_time, lat, lon, address, enabled
                    FROM shops
                    WHERE id = ?
                    LIMIT 1
                    """.trimIndent(),
                ).use { statement ->
                    statement.setLong(1, id)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.toShopEntity()
                        } else {
                            null
                        }
                    }
                }
            }
        }
    }

    override fun createShop(shop: CreateShopEntity): Result<ShopEntity> {
        return runCatching {
            val normalizedName = shop.name.trim()
            require(normalizedName.isNotEmpty()) {
                "Shop name is required"
            }

            DatabaseFactory.getConnection().use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO shops(
                        sort_order,
                        name,
                        city,
                        opening_time,
                        closing_time,
                        lat,
                        lon,
                        address,
                        enabled
                    )
                    VALUES (
                        (SELECT COALESCE(MIN(sort_order), 1) - 1 FROM shops),
                        ?, ?, ?, ?, ?, ?, ?, TRUE
                    )
                    RETURNING id, name, city, opening_time, closing_time, lat, lon, address, enabled
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, normalizedName)
                    statement.setNullableString(2, shop.city.normalizedOrNull())
                    statement.setString(3, DEFAULT_OPENING_TIME)
                    statement.setString(4, DEFAULT_CLOSING_TIME)
                    statement.setNullableDouble(5, shop.lat)
                    statement.setNullableDouble(6, shop.lon)
                    statement.setNullableString(7, shop.address.normalizedOrNull())
                    statement.executeQuery().use { resultSet ->
                        require(resultSet.next()) {
                            "Unable to create shop"
                        }
                        resultSet.toShopEntity()
                    }
                }
            }
        }
    }
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

    override fun getById(id: Long): Result<ShopEntity?> {
        return runCatching {
            loadShopsSnapshot().firstOrNull { it.id == id }
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
        private fun defaultShopsStoragePath(): String {
            return System.getenv("SHOPS_FILE_PATH")
                ?.takeIf(String::isNotBlank)
                ?: Paths.get(System.getProperty("user.dir"), "data", "stores.json").toString()
        }
    }
}

private const val MAX_PAGE_SIZE = 50
private const val DEFAULT_OPENING_TIME = "08:00"
private const val DEFAULT_CLOSING_TIME = "23:00"

private fun String?.normalizedOrNull(): String? {
    return this?.trim()?.takeIf(String::isNotEmpty)
}

private fun PreparedStatement.setSearchParameters(query: String) {
    val pattern = "%$query%"
    setString(1, query)
    setString(2, pattern)
    setString(3, pattern)
    setString(4, pattern)
}

private fun PreparedStatement.setNullableString(index: Int, value: String?) {
    if (value == null) {
        setNull(index, Types.VARCHAR)
    } else {
        setString(index, value)
    }
}

private fun PreparedStatement.setNullableDouble(index: Int, value: Double?) {
    if (value == null) {
        setNull(index, Types.DOUBLE)
    } else {
        setDouble(index, value)
    }
}

private fun ResultSet.toShopEntity(): ShopEntity {
    return ShopEntity(
        id = getLong("id"),
        name = getString("name"),
        city = getString("city"),
        openingTime = getString("opening_time"),
        closingTime = getString("closing_time"),
        lat = getNullableDouble("lat"),
        lon = getNullableDouble("lon"),
        address = getString("address"),
        enabled = getBoolean("enabled"),
    )
}

private fun ResultSet.getNullableDouble(columnName: String): Double? {
    val value = getDouble(columnName)
    return if (wasNull()) null else value
}
