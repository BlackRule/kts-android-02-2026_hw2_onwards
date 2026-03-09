package com.example.myapplication

import com.example.myapplication.server.repository.CreateShopEntity
import com.example.myapplication.server.repository.LoginRepository
import com.example.myapplication.server.repository.ShopEntity
import com.example.myapplication.server.repository.ShoppingListEntity
import com.example.myapplication.server.repository.ShoppingListWithShopEntity
import com.example.myapplication.server.repository.ShoppingListsRepository
import com.example.myapplication.server.repository.ShopsPageEntity
import com.example.myapplication.server.repository.ShopsRepository
import com.example.myapplication.server.repository.UpsertShoppingListEntity
import com.example.myapplication.server.repository.UserEntity
import com.example.myapplication.server.repository.UsersRepository
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

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

        override fun getById(id: Long): Result<ShopEntity?> {
            return Result.success(shops.firstOrNull { it.id == id })
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

    private val fakeShoppingListsRepository = object : ShoppingListsRepository {
        private val shoppingLists = mutableListOf(
            ShoppingListEntity(
                id = 1L,
                shopId = 60L,
                paidAt = "2026-03-08 18:05",
                totalAmountMinor = 1899L,
            ),
        )

        override fun getList(): Result<List<ShoppingListWithShopEntity>> {
            return Result.success(
                shoppingLists
                    .sortedByDescending { it.paidAt }
                    .map(::toWithShop),
            )
        }

        override fun getById(id: Long): Result<ShoppingListWithShopEntity?> {
            return Result.success(
                shoppingLists.firstOrNull { it.id == id }?.let(::toWithShop),
            )
        }

        override fun create(shoppingList: UpsertShoppingListEntity): Result<ShoppingListWithShopEntity> {
            val validatedShoppingList = validate(shoppingList)
            val createdShoppingList = ShoppingListEntity(
                id = (shoppingLists.maxOfOrNull { it.id } ?: 0L) + 1L,
                shopId = validatedShoppingList.shopId,
                paidAt = validatedShoppingList.paidAt,
                totalAmountMinor = validatedShoppingList.totalAmountMinor,
            )
            shoppingLists.add(createdShoppingList)
            return Result.success(toWithShop(createdShoppingList))
        }

        override fun update(
            id: Long,
            shoppingList: UpsertShoppingListEntity,
        ): Result<ShoppingListWithShopEntity?> {
            val validatedShoppingList = validate(shoppingList)
            val shoppingListIndex = shoppingLists.indexOfFirst { it.id == id }
            if (shoppingListIndex == -1) {
                return Result.success(null)
            }

            val updatedShoppingList = ShoppingListEntity(
                id = id,
                shopId = validatedShoppingList.shopId,
                paidAt = validatedShoppingList.paidAt,
                totalAmountMinor = validatedShoppingList.totalAmountMinor,
            )
            shoppingLists[shoppingListIndex] = updatedShoppingList
            return Result.success(toWithShop(updatedShoppingList))
        }

        override fun delete(id: Long): Result<Boolean> {
            return Result.success(shoppingLists.removeAll { it.id == id })
        }

        private fun validate(shoppingList: UpsertShoppingListEntity): ShoppingListEntity {
            require(shoppingList.totalAmountMinor >= 0L) {
                "Shopping list total must be zero or greater"
            }

            val normalizedPaidAt = shoppingList.paidAt.trim()
            require(normalizedPaidAt.isNotEmpty()) {
                "Payment time is required"
            }

            try {
                LocalDateTime.parse(normalizedPaidAt, paidAtFormatter)
            } catch (_: Exception) {
                throw IllegalArgumentException("Payment time must use yyyy-MM-dd HH:mm")
            }

            val shop = fakeShopsRepository.getById(shoppingList.shopId).getOrThrow()
                ?: throw IllegalArgumentException("Selected shop was not found")

            return ShoppingListEntity(
                id = 0L,
                shopId = shop.id,
                paidAt = normalizedPaidAt,
                totalAmountMinor = shoppingList.totalAmountMinor,
            )
        }

        private fun toWithShop(shoppingList: ShoppingListEntity): ShoppingListWithShopEntity {
            val shop = fakeShopsRepository.getById(shoppingList.shopId).getOrThrow()
                ?: error("Missing shop ${shoppingList.shopId}")
            return ShoppingListWithShopEntity(
                id = shoppingList.id,
                shop = shop,
                paidAt = shoppingList.paidAt,
                totalAmountMinor = shoppingList.totalAmountMinor,
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
                shopsRepository = fakeShopsRepository,
                shoppingListsRepository = fakeShoppingListsRepository,
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
                shoppingListsRepository = fakeShoppingListsRepository,
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
                shoppingListsRepository = fakeShoppingListsRepository,
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
                shoppingListsRepository = fakeShoppingListsRepository,
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
                shoppingListsRepository = fakeShoppingListsRepository,
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
                shoppingListsRepository = fakeShoppingListsRepository,
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
                shoppingListsRepository = fakeShoppingListsRepository,
            )
        }

        val response = client.post("/shops") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"   "}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "Shop name is required")
    }

    @Test
    fun testShoppingListsCrud() = testApplication {
        application {
            module(
                initializeDatabase = false,
                loginRepository = fakeLoginRepository,
                usersRepository = fakeUsersRepository,
                shopsRepository = fakeShopsRepository,
                shoppingListsRepository = fakeShoppingListsRepository,
            )
        }

        val createResponse = client.post("/shopping-lists") {
            contentType(ContentType.Application.Json)
            setBody("""{"shopId":61,"paidAt":"2026-03-09 12:40","totalAmountMinor":2599}""")
        }

        assertEquals(HttpStatusCode.Created, createResponse.status)
        assertContains(createResponse.bodyAsText(), "SPAR Center")
        assertContains(createResponse.bodyAsText(), "\"totalAmountMinor\":2599")

        val listResponse = client.get("/shopping-lists")

        assertEquals(HttpStatusCode.OK, listResponse.status)
        assertContains(listResponse.bodyAsText(), "\"shoppingLists\"")
        assertContains(listResponse.bodyAsText(), "\"paidAt\":\"2026-03-09 12:40\"")

        val updateResponse = client.put("/shopping-lists/1") {
            contentType(ContentType.Application.Json)
            setBody("""{"shopId":60,"paidAt":"2026-03-09 13:00","totalAmountMinor":3099}""")
        }

        assertEquals(HttpStatusCode.OK, updateResponse.status)
        assertContains(updateResponse.bodyAsText(), "\"totalAmountMinor\":3099")

        val getResponse = client.get("/shopping-lists/1")

        assertEquals(HttpStatusCode.OK, getResponse.status)
        assertContains(getResponse.bodyAsText(), "\"paidAt\":\"2026-03-09 13:00\"")

        val deleteResponse = client.delete("/shopping-lists/1")

        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val deletedResponse = client.get("/shopping-lists/1")

        assertEquals(HttpStatusCode.NotFound, deletedResponse.status)
    }

    @Test
    fun testCreateShoppingListRejectsInvalidPayload() = testApplication {
        application {
            module(
                initializeDatabase = false,
                loginRepository = fakeLoginRepository,
                usersRepository = fakeUsersRepository,
                shopsRepository = fakeShopsRepository,
                shoppingListsRepository = fakeShoppingListsRepository,
            )
        }

        val response = client.post("/shopping-lists") {
            contentType(ContentType.Application.Json)
            setBody("""{"shopId":999,"paidAt":"2026-03-09 13:00","totalAmountMinor":-1}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "Shopping list total must be zero or greater")
    }

    private companion object {
        private val paidAtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
