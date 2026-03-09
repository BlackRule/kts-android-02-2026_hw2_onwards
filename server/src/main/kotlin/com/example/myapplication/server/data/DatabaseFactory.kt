package com.example.myapplication.server.data

import com.example.myapplication.DEFAULT_LOGIN_PASSWORD
import com.example.myapplication.DEFAULT_LOGIN_USERNAME
import com.example.myapplication.server.repository.ShopEntity
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types

object DatabaseFactory {

    private val databaseUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/myapplication"
    private val databaseUser = System.getenv("DB_USER") ?: "myapp"
    private val databasePassword = System.getenv("DB_PASSWORD") ?: "myapp"
    private val configuredShopsSeedPath = System.getenv("SHOPS_FILE_PATH")
        ?.takeIf(String::isNotBlank)
        ?.let(Paths::get)
    private val json = Json {
        ignoreUnknownKeys = true
    }

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

    private fun createTables(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS auth_users (
                    id BIGSERIAL PRIMARY KEY,
                    username VARCHAR(128) NOT NULL UNIQUE,
                    password VARCHAR(128) NOT NULL
                )
                """.trimIndent(),
            )
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
        }
    }

    private fun seedData(connection: Connection) {
        seedAuthUsers(connection)
        seedUsers(connection)
        seedShops(connection)
    }

    private fun seedAuthUsers(connection: Connection) {
        connection.prepareStatement(
            """
            INSERT INTO auth_users(username, password)
            VALUES (?, ?)
            ON CONFLICT (username) DO NOTHING
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, DEFAULT_LOGIN_USERNAME)
            statement.setString(2, DEFAULT_LOGIN_PASSWORD)
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
}

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
