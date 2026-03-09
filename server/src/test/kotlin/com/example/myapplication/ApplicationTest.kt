package com.example.myapplication

import com.example.myapplication.server.repository.LoginRepository
import com.example.myapplication.server.repository.CreateShopEntity
import com.example.myapplication.server.repository.ShopEntity
import com.example.myapplication.server.repository.ShopsPageEntity
import com.example.myapplication.server.repository.ShopsRepository
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

    private val fakeShopsRepository = object : ShopsRepository {
        private val shops = mutableListOf(
            ShopEntity(
                id = 60L,
                name = "Универсам \"Спар №60\"",
                city = "Калининград г",
                openingTime = "08:00",
                closingTime = "23:00",
                lat = 54.74686,
                lon = 20.48272,
                address = "SPAR на Елизаветинской, 11",
                enabled = true,
            ),
            ShopEntity(
                id = 61L,
                name = "SPAR Center",
                city = "Калининград г",
                openingTime = "08:00",
                closingTime = "23:00",
                lat = 54.7,
                lon = 20.4,
                address = "Ленинский проспект, 5",
                enabled = true,
            ),
            ShopEntity(
                id = 62L,
                name = "Market House",
                city = "Советск",
                openingTime = "09:00",
                closingTime = "22:00",
                lat = 55.1,
                lon = 21.9,
                address = "Победы, 3",
                enabled = false,
            ),
        )

        override fun getList(
            query: String,
            page: Int,
            pageSize: Int,
        ): Result<ShopsPageEntity> {
            val filtered = if (query.isBlank()) {
                shops
            } else {
                shops.filter { shop ->
                    shop.name.contains(query, ignoreCase = true) ||
                        shop.city.contains(query, ignoreCase = true) ||
                        shop.address.contains(query, ignoreCase = true)
                }
            }
            val fromIndex = ((page - 1) * pageSize).coerceAtMost(filtered.size)
            val toIndex = (fromIndex + pageSize).coerceAtMost(filtered.size)
            val pageItems = if (fromIndex >= toIndex) emptyList() else filtered.subList(fromIndex, toIndex)

            return Result.success(
                ShopsPageEntity(
                    shops = pageItems,
                    page = page,
                    pageSize = pageSize,
                    totalCount = filtered.size,
                    hasNextPage = toIndex < filtered.size,
                ),
            )
        }

        override fun createShop(shop: CreateShopEntity): Result<ShopEntity> {
            val normalizedName = shop.name.trim()
            if (normalizedName.isEmpty()) {
                return Result.failure(IllegalArgumentException("Shop name is required"))
            }

            val createdShop = ShopEntity(
                id = (shops.maxOfOrNull { it.id } ?: 0L) + 1L,
                name = normalizedName,
                city = shop.city,
                openingTime = "08:00",
                closingTime = "23:00",
                lat = shop.lat,
                lon = shop.lon,
                address = shop.address,
                enabled = true,
            )
            shops.add(0, createdShop)
            return Result.success(createdShop)
        }
    }

    @Test
    fun testRoot() = testApplication {
        application {
            module(
                initializeDatabase = false,
                loginRepository = fakeLoginRepository,
                usersRepository = fakeUsersRepository,
                shopsRepository = fakeShopsRepository,
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
                shopsRepository = fakeShopsRepository,
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
                shopsRepository = fakeShopsRepository,
            )
        }

        val response = client.get("/users")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Test User")
    }

    @Test
    fun testShopsList() = testApplication {
        application {
            module(
                initializeDatabase = false,
                loginRepository = fakeLoginRepository,
                usersRepository = fakeUsersRepository,
                shopsRepository = fakeShopsRepository,
            )
        }

        val response = client.get("/shops")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "SPAR Center")
    }

    @Test
    fun testStoresAliasSupportsSearchAndPagination() = testApplication {
        application {
            module(
                initializeDatabase = false,
                loginRepository = fakeLoginRepository,
                usersRepository = fakeUsersRepository,
                shopsRepository = fakeShopsRepository,
            )
        }

        val response = client.get("/stores") {
            parameter("query", "Калининград")
            parameter("page", 2)
            parameter("pageSize", 1)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"page\":2")
        assertContains(response.bodyAsText(), "SPAR Center")
        assertContains(response.bodyAsText(), "\"hasNextPage\":false")
    }

    @Test
    fun testCreateShop() = testApplication {
        application {
            module(
                initializeDatabase = false,
                loginRepository = fakeLoginRepository,
                usersRepository = fakeUsersRepository,
                shopsRepository = fakeShopsRepository,
            )
        }

        val createResponse = client.post("/shops") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"New Test Shop","city":"Калининград","lat":54.71,"lon":20.5,"address":"Тестовая, 1"}""")
        }

        assertEquals(HttpStatusCode.Created, createResponse.status)
        assertContains(createResponse.bodyAsText(), "New Test Shop")

        val listResponse = client.get("/shops") {
            parameter("query", "New Test Shop")
        }

        assertEquals(HttpStatusCode.OK, listResponse.status)
        assertContains(listResponse.bodyAsText(), "New Test Shop")
    }

    @Test
    fun testCreateShopRejectsBlankName() = testApplication {
        application {
            module(
                initializeDatabase = false,
                loginRepository = fakeLoginRepository,
                usersRepository = fakeUsersRepository,
                shopsRepository = fakeShopsRepository,
            )
        }

        val response = client.post("/shops") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"   "}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "Shop name is required")
    }
}
