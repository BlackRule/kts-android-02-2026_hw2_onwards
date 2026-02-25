package com.example.myapplication.server.repository

import com.example.myapplication.server.data.DatabaseFactory

interface LoginRepository {
    fun login(
        username: String,
        password: String,
    ): Result<Unit>
}

class PostgresLoginRepository : LoginRepository {

    override fun login(
        username: String,
        password: String,
    ): Result<Unit> {
        return try {
            val isValid = DatabaseFactory.getConnection().use { connection ->
                connection.prepareStatement(
                    """
                    SELECT 1
                    FROM auth_users
                    WHERE username = ? AND password = ?
                    LIMIT 1
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, username.trim())
                    statement.setString(2, password)
                    statement.executeQuery().use { resultSet ->
                        resultSet.next()
                    }
                }
            }

            if (isValid) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Invalid username or password"))
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}
