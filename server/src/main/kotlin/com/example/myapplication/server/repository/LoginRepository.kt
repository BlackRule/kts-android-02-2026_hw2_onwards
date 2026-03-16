package com.example.myapplication.server.repository

import com.example.myapplication.server.data.DatabaseFactory

data class AuthenticatedUserEntity(
    val id: Long,
    val username: String,
    val fullName: String,
    val position: String,
)

interface LoginRepository {
    fun login(
        username: String,
        password: String,
    ): Result<AuthenticatedUserEntity>
}

class PostgresLoginRepository : LoginRepository {

    override fun login(
        username: String,
        password: String,
    ): Result<AuthenticatedUserEntity> {
        return try {
            val authenticatedUser = DatabaseFactory.getConnection().use { connection ->
                connection.prepareStatement(
                    """
                    SELECT u.id, a.username, u.full_name, u.position
                    FROM auth_users a
                    JOIN users u ON u.id = a.user_id
                    WHERE a.username = ? AND a.password = ?
                    LIMIT 1
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, username.trim())
                    statement.setString(2, password)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            AuthenticatedUserEntity(
                                id = resultSet.getLong("id"),
                                username = resultSet.getString("username"),
                                fullName = resultSet.getString("full_name"),
                                position = resultSet.getString("position"),
                            )
                        } else {
                            null
                        }
                    }
                }
            }

            if (authenticatedUser != null) {
                Result.success(authenticatedUser)
            } else {
                Result.failure(IllegalArgumentException("Invalid username or password"))
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}
