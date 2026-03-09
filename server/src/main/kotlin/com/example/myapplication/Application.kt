package com.example.myapplication

import com.example.myapplication.server.api.ErrorResponse
import com.example.myapplication.server.api.LoginRequest
import com.example.myapplication.server.api.LoginResponse
import com.example.myapplication.server.api.UserResponse
import com.example.myapplication.server.api.UsersListResponse
import com.example.myapplication.server.data.DatabaseFactory
import com.example.myapplication.server.repository.LoginRepository
import com.example.myapplication.server.repository.PostgresLoginRepository
import com.example.myapplication.server.repository.PostgresUsersRepository
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
    embeddedServer(Netty, port = serverPort, host = "192.168.1.76", module = Application::module)
        .start(wait = true)
}

fun Application.module(
    initializeDatabase: Boolean = true,
    loginRepository: LoginRepository = PostgresLoginRepository(),
    usersRepository: UsersRepository = PostgresUsersRepository(),
) {
    install(ContentNegotiation) {
        json()
    }

    if (initializeDatabase) {
        DatabaseFactory.initialize()
    }

    routing {
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
    }
}
