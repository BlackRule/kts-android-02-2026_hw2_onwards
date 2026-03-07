package com.example.myapplication

import com.example.myapplication.server.api.ErrorResponse
import com.example.myapplication.server.api.LoginRequest
import com.example.myapplication.server.api.LoginResponse
import com.example.myapplication.server.api.ShopResponse
import com.example.myapplication.server.api.ShopsListResponse
import com.example.myapplication.server.api.UserResponse
import com.example.myapplication.server.api.UsersListResponse
import com.example.myapplication.server.data.DatabaseFactory
import com.example.myapplication.server.repository.JsonShopsRepository
import com.example.myapplication.server.repository.LoginRepository
import com.example.myapplication.server.repository.PostgresLoginRepository
import com.example.myapplication.server.repository.PostgresUsersRepository
import com.example.myapplication.server.repository.ShopsRepository
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

fun main() {
    val serverPort = System.getenv("SERVER_PORT")?.toIntOrNull() ?: SERVER_PORT
    val serverHost = System.getenv("SERVER_HOST") ?: "0.0.0.0"
    embeddedServer(Netty, port = serverPort, host = serverHost, module = Application::module)
        .start(wait = true)
}

fun Application.module(
    initializeDatabase: Boolean = true,
    loginRepository: LoginRepository = PostgresLoginRepository(),
    usersRepository: UsersRepository = PostgresUsersRepository(),
    shopsRepository: ShopsRepository = JsonShopsRepository(),
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
                            ShopResponse(
                                id = shop.id,
                                name = shop.name,
                                city = shop.city,
                                openingTime = shop.openingTime,
                                closingTime = shop.closingTime,
                                lat = shop.lat,
                                lon = shop.lon,
                                address = shop.address,
                                enabled = shop.enabled,
                            )
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

        get("/stores") {
            call.respondWithShops()
        }
    }
}
