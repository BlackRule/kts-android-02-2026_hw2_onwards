package com.example.myapplication.server.data

import com.example.myapplication.DEFAULT_LOGIN_PASSWORD
import com.example.myapplication.DEFAULT_LOGIN_USERNAME
import com.example.myapplication.server.repository.ShopEntity
import kotlinx.serialization.json.Json
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types

object DatabaseFactory {

    private val databaseUrl = resolveDatabaseUrl()
    private val databaseUser = System.getenv("DB_USER") ?: "myapp"
    private val databasePassword = System.getenv("DB_PASSWORD") ?: "myapp"
    private val configuredShopsSeedPath = System.getenv("SHOPS_FILE_PATH")
        ?.takeIf(String::isNotBlank)
        ?.let(Paths::get)
    private val json = Json {
        ignoreUnknownKeys = true
    }
    private const val DEFAULT_AUTH_USER_ID = 2L

    init {
        Class.forName("org.postgresql.Driver")
    }

    fun initialize() {
        getConnection().use { connection ->
            createTables(connection)
            seedData(connection)
        }
    }

    fun getConnection(): Connection = DriverManager.getConnection(databaseUrl, databaseUser, databasePassword)

    private fun resolveDatabaseUrl(
        environment: Map<String, String> = System.getenv(),
    ): String {
        environment["DB_URL"]
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { return it }

        val configuredServerBaseUrl = environment["SERVER_BASE_URL"]
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: DEFAULT_SERVER_BASE_URL

        val databaseHost = runCatching {
            URI.create(configuredServerBaseUrl).host
        }.getOrNull()
            ?.takeIf { it.isNotBlank() && it != "0.0.0.0" }
            ?: environment["SERVER_HOST"]
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it != "0.0.0.0" }
            ?: DEFAULT_SERVER_HOST

        return "jdbc:postgresql://$databaseHost:5432/myapplication"
    }

    private fun createTables(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS users (
                    id BIGINT PRIMARY KEY,
                    full_name VARCHAR(255) NOT NULL,
                    position VARCHAR(255) NOT NULL
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS auth_users (
                    id BIGSERIAL PRIMARY KEY,
                    username VARCHAR(128) NOT NULL UNIQUE,
                    password VARCHAR(128) NOT NULL,
                    user_id BIGINT REFERENCES users(id)
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                ALTER TABLE auth_users
                ADD COLUMN IF NOT EXISTS user_id BIGINT
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS shops (
                    id BIGSERIAL PRIMARY KEY,
                    sort_order BIGINT NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    city VARCHAR(255),
                    opening_time VARCHAR(16) NOT NULL,
                    closing_time VARCHAR(16) NOT NULL,
                    lat DOUBLE PRECISION,
                    lon DOUBLE PRECISION,
                    address TEXT,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS units (
                    code VARCHAR(32) PRIMARY KEY
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS items (
                    barcode VARCHAR(64) PRIMARY KEY,
                    main_name TEXT NOT NULL,
                    unit_code VARCHAR(32) NOT NULL REFERENCES units(code)
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS item_names (
                    id BIGSERIAL PRIMARY KEY,
                    item_barcode VARCHAR(64) NOT NULL REFERENCES items(barcode) ON DELETE CASCADE,
                    name TEXT NOT NULL,
                    CONSTRAINT uq_item_names UNIQUE(item_barcode, name)
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS price_observations (
                    id BIGSERIAL PRIMARY KEY,
                    shop_id BIGINT NOT NULL REFERENCES shops(id),
                    item_barcode VARCHAR(64) NOT NULL REFERENCES items(barcode),
                    price NUMERIC(12, 2) NOT NULL,
                    discount_percent NUMERIC(5, 2) NOT NULL,
                    final_price NUMERIC(12, 2) NOT NULL,
                    payment_time TIMESTAMP NOT NULL
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_price_observations_identity
                ON price_observations(shop_id, item_barcode, price, final_price)
                """.trimIndent(),
            )
        }
    }

    private fun seedData(connection: Connection) {
        seedUsers(connection)
        seedAuthUsers(connection)
        seedShops(connection)
        seedUnits(connection)
    }

    private fun seedAuthUsers(connection: Connection) {
        connection.prepareStatement(
            """
            INSERT INTO auth_users(username, password, user_id)
            VALUES (?, ?, ?)
            ON CONFLICT (username) DO UPDATE
            SET password = EXCLUDED.password,
                user_id = EXCLUDED.user_id
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, DEFAULT_LOGIN_USERNAME)
            statement.setString(2, DEFAULT_LOGIN_PASSWORD)
            statement.setLong(3, DEFAULT_AUTH_USER_ID)
            statement.executeUpdate()
        }
    }

    private fun seedUsers(connection: Connection) {
        val seedUsers = listOf(
            Triple(1L, "Sophia Turner", "Product Manager"),
            Triple(2L, "Liam Johnson", "Android Engineer"),
            Triple(3L, "Olivia Brown", "QA Engineer"),
            Triple(4L, "Noah Davis", "UX Designer"),
            Triple(5L, "Emma Wilson", "Backend Engineer"),
            Triple(6L, "James Miller", "Data Analyst"),
        )

        connection.prepareStatement(
            """
            INSERT INTO users(id, full_name, position)
            VALUES (?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
        ).use { statement ->
            seedUsers.forEach { (id, fullName, position) ->
                statement.setLong(1, id)
                statement.setString(2, fullName)
                statement.setString(3, position)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun seedShops(connection: Connection) {
        val hasExistingShops = connection.prepareStatement(
            """
            SELECT EXISTS(SELECT 1 FROM shops)
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { resultSet ->
                resultSet.next()
                resultSet.getBoolean(1)
            }
        }

        if (!hasExistingShops) {
            val seedShops = loadSeedShops()
            connection.prepareStatement(
                """
                INSERT INTO shops(
                    id,
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
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """.trimIndent(),
            ).use { statement ->
                seedShops.forEachIndexed { index, shop ->
                    statement.setLong(1, shop.id)
                    statement.setLong(2, index.toLong())
                    statement.setString(3, shop.name)
                    statement.setNullableString(4, shop.city)
                    statement.setString(5, shop.openingTime)
                    statement.setString(6, shop.closingTime)
                    statement.setNullableDouble(7, shop.lat)
                    statement.setNullableDouble(8, shop.lon)
                    statement.setNullableString(9, shop.address)
                    statement.setBoolean(10, shop.enabled)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }

        syncShopsSequence(connection)
    }

    private fun loadSeedShops(): List<ShopEntity> {
        return json.decodeFromString<List<ShopEntity>>(readSeedShopsJson())
    }

    private fun readSeedShopsJson(): String {
        val candidatePaths = buildList {
            configuredShopsSeedPath?.let(::add)
            add(Paths.get(System.getProperty("user.dir"), "data", "stores.json"))
        }

        candidatePaths.firstOrNull(Files::isRegularFile)?.let { path ->
            return Files.readString(path, StandardCharsets.UTF_8)
        }

        val resourceStream = DatabaseFactory::class.java.classLoader.getResourceAsStream("stores.json")
            ?: error("Missing shops seed resource: stores.json")

        return resourceStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            reader.readText()
        }
    }

    private fun syncShopsSequence(connection: Connection) {
        connection.prepareStatement(
            """
            SELECT setval(
                pg_get_serial_sequence('shops', 'id'),
                COALESCE((SELECT MAX(id) FROM shops), 1),
                EXISTS(SELECT 1 FROM shops)
            )
            """.trimIndent(),
        ).use { statement ->
            statement.execute()
        }
    }

    private fun seedUnits(connection: Connection) {
        connection.prepareStatement(
            """
            INSERT INTO units(code)
            VALUES (?), (?)
            ON CONFLICT (code) DO NOTHING
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, "PIECE")
            statement.setString(2, "KG")
            statement.executeUpdate()
        }
    }
}

private const val DEFAULT_SERVER_HOST = "195.46.171.236"
private const val DEFAULT_SERVER_BASE_URL = "https://$DEFAULT_SERVER_HOST:9878"

private fun java.sql.PreparedStatement.setNullableString(index: Int, value: String?) {
    if (value == null) {
        setNull(index, Types.VARCHAR)
    } else {
        setString(index, value)
    }
}

private fun java.sql.PreparedStatement.setNullableDouble(index: Int, value: Double?) {
    if (value == null) {
        setNull(index, Types.DOUBLE)
    } else {
        setDouble(index, value)
    }
}
