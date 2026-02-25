package com.example.myapplication.server.data

import com.example.myapplication.DEFAULT_LOGIN_PASSWORD
import com.example.myapplication.DEFAULT_LOGIN_USERNAME
import java.sql.Connection
import java.sql.DriverManager

object DatabaseFactory {

    private val databaseUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/myapplication"
    private val databaseUser = System.getenv("DB_USER") ?: "myapp"
    private val databasePassword = System.getenv("DB_PASSWORD") ?: "myapp"

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
        }
    }

    private fun seedData(connection: Connection) {
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
}
