package com.example.myapplication.server.repository

import com.example.myapplication.server.data.DatabaseFactory

data class UserEntity(
    val id: Long,
    val fullName: String,
    val position: String,
)

interface UsersRepository {
    fun getList(): Result<List<UserEntity>>
}

class PostgresUsersRepository : UsersRepository {

    override fun getList(): Result<List<UserEntity>> {
        return runCatching {
            DatabaseFactory.getConnection().use { connection ->
                connection.prepareStatement(
                    """
                    SELECT id, full_name, position
                    FROM users
                    ORDER BY id
                    """.trimIndent(),
                ).use { statement ->
                    statement.executeQuery().use { resultSet ->
                        buildList {
                            while (resultSet.next()) {
                                add(
                                    UserEntity(
                                        id = resultSet.getLong("id"),
                                        fullName = resultSet.getString("full_name"),
                                        position = resultSet.getString("position"),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
