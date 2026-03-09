package com.example.myapplication

import com.example.myapplication.server.repository.LoginRepository
import com.example.myapplication.server.repository.UserEntity
import com.example.myapplication.server.repository.UsersRepository
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.ContentType
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {

    private val fakeLoginRepository = object : LoginRepository {
        override fun login(
            username: String,
            password: String,
        ): Result<Unit> {
            return if (username == DEFAULT_LOGIN_USERNAME && password == DEFAULT_LOGIN_PASSWORD) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Invalid username or password"))
            }
        }
    }

    private val fakeUsersRepository = object : UsersRepository {
        override fun getList(): Result<List<UserEntity>> {
            return Result.success(
                listOf(
                    UserEntity(id = 1L, fullName = "Test User", position = "Android Engineer"),
                ),
            )
        }
    }

    @Test
    fun testRoot() = testApplication {
        application {
            module(
                initializeDatabase = false,
                loginRepository = fakeLoginRepository,
                usersRepository = fakeUsersRepository,
            )
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Server is running", response.bodyAsText())
    }

    @Test
    fun testLoginSuccess() = testApplication {
        application {
            module(
                initializeDatabase = false,
                loginRepository = fakeLoginRepository,
                usersRepository = fakeUsersRepository,
            )
        }

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"admin123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"success\":true")
    }

    @Test
    fun testUsersList() = testApplication {
        application {
            module(
                initializeDatabase = false,
                loginRepository = fakeLoginRepository,
                usersRepository = fakeUsersRepository,
            )
        }

        val response = client.get("/users")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Test User")
    }
}
