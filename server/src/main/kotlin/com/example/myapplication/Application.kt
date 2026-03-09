package com.example.myapplication

import com.example.myapplication.server.api.ErrorResponse
import com.example.myapplication.server.api.CreateShopRequest
import com.example.myapplication.server.api.LoginRequest
import com.example.myapplication.server.api.LoginResponse
import com.example.myapplication.server.api.ShopResponse
import com.example.myapplication.server.api.ShoppingListResponse
import com.example.myapplication.server.api.ShoppingListsListResponse
import com.example.myapplication.server.api.ShopsListResponse
import com.example.myapplication.server.api.UpsertShoppingListRequest
import com.example.myapplication.server.api.UserResponse
import com.example.myapplication.server.api.UsersListResponse
import com.example.myapplication.server.data.DatabaseFactory
import com.example.myapplication.server.repository.CreateShopEntity
import com.example.myapplication.server.repository.JsonShoppingListsRepository
import com.example.myapplication.server.repository.LoginRepository
import com.example.myapplication.server.repository.PostgresLoginRepository
import com.example.myapplication.server.repository.PostgresShopsRepository
import com.example.myapplication.server.repository.PostgresUsersRepository
import com.example.myapplication.server.repository.ShopEntity
import com.example.myapplication.server.repository.ShoppingListWithShopEntity
import com.example.myapplication.server.repository.ShoppingListsRepository
import com.example.myapplication.server.repository.ShopsRepository
import com.example.myapplication.server.repository.UpsertShoppingListEntity
import com.example.myapplication.server.repository.UsersRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import java.io.File
import java.security.KeyStore

fun main() {
    val runtimeConfig = serverRuntimeConfigFromEnvironment()
    embeddedServer(
        factory = Netty,
        environment = applicationEnvironment { },
        configure = {
            applyConnectors(runtimeConfig)
        },
        module = Application::module,
    )
        .start(wait = true)
}

internal data class ServerRuntimeConfig(
    val host: String,
    val port: Int,
    val tls: TlsConfig?,
)

internal data class TlsConfig(
    val keyStorePath: String,
    val keyStorePassword: String,
    val privateKeyPassword: String,
    val keyAlias: String,
    val keyStoreType: String,
)

internal fun serverRuntimeConfigFromEnvironment(
    environment: Map<String, String> = System.getenv(),
): ServerRuntimeConfig {
    val host = environment["SERVER_HOST"]?.takeIf(String::isNotBlank) ?: "0.0.0.0"
    val port = environment["SERVER_PORT"]?.toIntOrNull() ?: DEFAULT_SERVER_PORT

    return ServerRuntimeConfig(
        host = host,
        port = port,
        tls = tlsConfigFromEnvironment(environment),
    )
}

internal fun ApplicationEngine.Configuration.applyConnectors(config: ServerRuntimeConfig) {
    val tlsConfig = config.tls
    if (tlsConfig == null) {
        connector {
            port = config.port
            host = config.host
        }
    } else {
        sslConnector(
            keyStore = loadKeyStore(tlsConfig),
            keyAlias = tlsConfig.keyAlias,
            keyStorePassword = { tlsConfig.keyStorePassword.toCharArray() },
            privateKeyPassword = { tlsConfig.privateKeyPassword.toCharArray() },
        ) {
            port = config.port
            host = config.host
        }
    }
}

private fun tlsConfigFromEnvironment(environment: Map<String, String>): TlsConfig? {
    val keyStorePath = environment["SSL_KEYSTORE_PATH"].orEmpty().trim()
    val keyStorePassword = environment["SSL_KEYSTORE_PASSWORD"].orEmpty()
    val privateKeyPassword = environment["SSL_PRIVATE_KEY_PASSWORD"].orEmpty()
    val keyAlias = environment["SSL_KEY_ALIAS"].orEmpty().trim()
    val hasTlsConfiguration = listOf(
        keyStorePath,
        keyStorePassword,
        privateKeyPassword,
        keyAlias,
    ).any(String::isNotBlank)

    if (!hasTlsConfiguration) {
        return null
    }

    val missingVariables = buildList {
        if (keyStorePath.isBlank()) add("SSL_KEYSTORE_PATH")
        if (keyStorePassword.isBlank()) add("SSL_KEYSTORE_PASSWORD")
        if (privateKeyPassword.isBlank()) add("SSL_PRIVATE_KEY_PASSWORD")
        if (keyAlias.isBlank()) add("SSL_KEY_ALIAS")
    }

    require(missingVariables.isEmpty()) {
        "Incomplete HTTPS configuration. Missing: ${missingVariables.joinToString()}"
    }

    return TlsConfig(
        keyStorePath = keyStorePath,
        keyStorePassword = keyStorePassword,
        privateKeyPassword = privateKeyPassword,
        keyAlias = keyAlias,
        keyStoreType = environment["SSL_KEYSTORE_TYPE"]?.takeIf(String::isNotBlank) ?: "PKCS12",
    )
}

private fun loadKeyStore(tlsConfig: TlsConfig): KeyStore {
    val keyStoreFile = File(tlsConfig.keyStorePath)
    require(keyStoreFile.isFile) {
        "HTTPS keystore file not found: ${keyStoreFile.absolutePath}"
    }

    return KeyStore.getInstance(tlsConfig.keyStoreType).apply {
        keyStoreFile.inputStream().use { input ->
            load(input, tlsConfig.keyStorePassword.toCharArray())
        }
    }
}

fun Application.module(
    initializeDatabase: Boolean = true,
    loginRepository: LoginRepository = PostgresLoginRepository(),
    usersRepository: UsersRepository = PostgresUsersRepository(),
    shopsRepository: ShopsRepository = PostgresShopsRepository(),
    shoppingListsRepository: ShoppingListsRepository = JsonShoppingListsRepository(shopsRepository = shopsRepository),
) {
    install(ContentNegotiation) {
        json()
    }

    if (initializeDatabase) {
        DatabaseFactory.initialize()
    }

    routing {
        suspend fun ApplicationCall.respondWithShops() {
            val query = request.queryParameters["query"].orEmpty()
            val page = request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = request.queryParameters["pageSize"]?.toIntOrNull()
                ?: request.queryParameters["limit"]?.toIntOrNull()
                ?: DEFAULT_SHOPS_PAGE_SIZE

            val shopsResult = shopsRepository.getList(
                query = query,
                page = page,
                pageSize = pageSize,
            )

            if (shopsResult.isSuccess) {
                val shopsPage = shopsResult.getOrThrow()
                respond(
                    status = HttpStatusCode.OK,
                    message = ShopsListResponse(
                        shops = shopsPage.shops.map { shop ->
                            shop.toResponse()
                        },
                        page = shopsPage.page,
                        pageSize = shopsPage.pageSize,
                        totalCount = shopsPage.totalCount,
                        hasNextPage = shopsPage.hasNextPage,
                    ),
                )
            } else {
                val exception = shopsResult.exceptionOrNull()
                respond(
                    status = HttpStatusCode.InternalServerError,
                    message = ErrorResponse(exception?.message ?: "Unable to load shops"),
                )
            }
        }

        suspend fun ApplicationCall.respondWithCreatedShop() {
            val request = receive<CreateShopRequest>()
            val createResult = shopsRepository.createShop(
                CreateShopEntity(
                    name = request.name,
                    city = request.city,
                    lat = request.lat,
                    lon = request.lon,
                    address = request.address,
                ),
            )

            if (createResult.isSuccess) {
                respond(
                    status = HttpStatusCode.Created,
                    message = createResult.getOrThrow().toResponse(),
                )
            } else {
                val exception = createResult.exceptionOrNull()
                respond(
                    status = if (exception is IllegalArgumentException) {
                        HttpStatusCode.BadRequest
                    } else {
                        HttpStatusCode.InternalServerError
                    },
                    message = ErrorResponse(exception?.message ?: "Unable to create shop"),
                )
            }
        }

        suspend fun ApplicationCall.respondWithShoppingLists() {
            val shoppingListsResult = shoppingListsRepository.getList()

            if (shoppingListsResult.isSuccess) {
                respond(
                    status = HttpStatusCode.OK,
                    message = ShoppingListsListResponse(
                        shoppingLists = shoppingListsResult.getOrThrow().map { it.toResponse() },
                    ),
                )
            } else {
                val exception = shoppingListsResult.exceptionOrNull()
                respond(
                    status = HttpStatusCode.InternalServerError,
                    message = ErrorResponse(exception?.message ?: "Unable to load shopping lists"),
                )
            }
        }

        suspend fun ApplicationCall.respondWithShoppingList(id: Long) {
            val shoppingListResult = shoppingListsRepository.getById(id)

            if (shoppingListResult.isFailure) {
                val exception = shoppingListResult.exceptionOrNull()
                respond(
                    status = HttpStatusCode.InternalServerError,
                    message = ErrorResponse(exception?.message ?: "Unable to load shopping list"),
                )
                return
            }

            val shoppingList = shoppingListResult.getOrThrow()
            if (shoppingList == null) {
                respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponse("Shopping list not found"),
                )
            } else {
                respond(
                    status = HttpStatusCode.OK,
                    message = shoppingList.toResponse(),
                )
            }
        }

        suspend fun ApplicationCall.respondWithCreatedShoppingList() {
            val request = receive<UpsertShoppingListRequest>()
            val createResult = shoppingListsRepository.create(
                UpsertShoppingListEntity(
                    shopId = request.shopId,
                    paidAt = request.paidAt,
                    totalAmountMinor = request.totalAmountMinor,
                ),
            )

            if (createResult.isSuccess) {
                respond(
                    status = HttpStatusCode.Created,
                    message = createResult.getOrThrow().toResponse(),
                )
            } else {
                val exception = createResult.exceptionOrNull()
                respond(
                    status = if (exception is IllegalArgumentException) {
                        HttpStatusCode.BadRequest
                    } else {
                        HttpStatusCode.InternalServerError
                    },
                    message = ErrorResponse(exception?.message ?: "Unable to create shopping list"),
                )
            }
        }

        suspend fun ApplicationCall.respondWithUpdatedShoppingList(id: Long) {
            val request = receive<UpsertShoppingListRequest>()
            val updateResult = shoppingListsRepository.update(
                id = id,
                shoppingList = UpsertShoppingListEntity(
                    shopId = request.shopId,
                    paidAt = request.paidAt,
                    totalAmountMinor = request.totalAmountMinor,
                ),
            )

            if (updateResult.isFailure) {
                val exception = updateResult.exceptionOrNull()
                respond(
                    status = if (exception is IllegalArgumentException) {
                        HttpStatusCode.BadRequest
                    } else {
                        HttpStatusCode.InternalServerError
                    },
                    message = ErrorResponse(exception?.message ?: "Unable to update shopping list"),
                )
                return
            }

            val updatedShoppingList = updateResult.getOrThrow()
            if (updatedShoppingList == null) {
                respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponse("Shopping list not found"),
                )
            } else {
                respond(
                    status = HttpStatusCode.OK,
                    message = updatedShoppingList.toResponse(),
                )
            }
        }

        suspend fun ApplicationCall.respondWithDeletedShoppingList(id: Long) {
            val deleteResult = shoppingListsRepository.delete(id)

            if (deleteResult.isFailure) {
                val exception = deleteResult.exceptionOrNull()
                respond(
                    status = HttpStatusCode.InternalServerError,
                    message = ErrorResponse(exception?.message ?: "Unable to delete shopping list"),
                )
                return
            }

            if (deleteResult.getOrThrow()) {
                respond(status = HttpStatusCode.NoContent, message = "")
            } else {
                respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponse("Shopping list not found"),
                )
            }
        }

        get("/") {
            call.respondText("Server is running")
        }

        post("/auth/login") {
            val request = call.receive<LoginRequest>()
            val loginResult = loginRepository.login(
                username = request.username,
                password = request.password,
            )

            if (loginResult.isSuccess) {
                call.respond(
                    status = HttpStatusCode.OK,
                    message = LoginResponse(success = true, message = "Login successful"),
                )
            } else {
                call.respond(
                    status = HttpStatusCode.Unauthorized,
                    message = LoginResponse(
                        success = false,
                        message = loginResult.exceptionOrNull()?.message ?: "Invalid username or password",
                    ),
                )
            }
        }

        get("/users") {
            val usersResult = usersRepository.getList()

            if (usersResult.isSuccess) {
                val users = usersResult.getOrDefault(emptyList())
                call.respond(
                    status = HttpStatusCode.OK,
                    message = UsersListResponse(
                        users = users.map { user ->
                            UserResponse(
                                id = user.id,
                                fullName = user.fullName,
                                position = user.position,
                            )
                        },
                    )
                )
            } else {
                val exception = usersResult.exceptionOrNull()
                call.respond(
                    status = HttpStatusCode.InternalServerError,
                    message = ErrorResponse(exception?.message ?: "Unable to load users"),
                )
            }
        }

        get("/shops") {
            call.respondWithShops()
        }

        post("/shops") {
            call.respondWithCreatedShop()
        }

        get("/stores") {
            call.respondWithShops()
        }

        post("/stores") {
            call.respondWithCreatedShop()
        }

        get("/shopping-lists") {
            call.respondWithShoppingLists()
        }

        get("/shopping-lists/{shoppingListId}") {
            val shoppingListId = call.parameters["shoppingListId"]?.toLongOrNull()
            if (shoppingListId == null) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = ErrorResponse("Invalid shopping list id"),
                )
            } else {
                call.respondWithShoppingList(shoppingListId)
            }
        }

        post("/shopping-lists") {
            call.respondWithCreatedShoppingList()
        }

        put("/shopping-lists/{shoppingListId}") {
            val shoppingListId = call.parameters["shoppingListId"]?.toLongOrNull()
            if (shoppingListId == null) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = ErrorResponse("Invalid shopping list id"),
                )
            } else {
                call.respondWithUpdatedShoppingList(shoppingListId)
            }
        }

        delete("/shopping-lists/{shoppingListId}") {
            val shoppingListId = call.parameters["shoppingListId"]?.toLongOrNull()
            if (shoppingListId == null) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = ErrorResponse("Invalid shopping list id"),
                )
            } else {
                call.respondWithDeletedShoppingList(shoppingListId)
            }
        }
    }
}

private fun ShopEntity.toResponse(): ShopResponse {
    return ShopResponse(
        id = id,
        name = name,
        city = city,
        openingTime = openingTime,
        closingTime = closingTime,
        lat = lat,
        lon = lon,
        address = address,
        enabled = enabled,
    )
}

private fun ShoppingListWithShopEntity.toResponse(): ShoppingListResponse {
    return ShoppingListResponse(
        id = id,
        shop = shop.toResponse(),
        paidAt = paidAt,
        totalAmountMinor = totalAmountMinor,
    )
}
