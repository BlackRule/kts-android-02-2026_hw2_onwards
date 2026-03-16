package com.example.myapplication.feature.login.repository

import com.example.myapplication.core.network.AppHttpClient
import com.example.myapplication.feature.profile.model.LoggedInProfile
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

data class LoginResult(
    val token: String,
    val profile: LoggedInProfile,
)

class LoginRepository(
    private val httpClient: HttpClient = AppHttpClient.instance,
) {

    suspend fun login(
        username: String,
        password: String,
    ): Result<LoginResult> {
        return withContext(Dispatchers.Default) {
            try {
                val response = httpClient.post("/auth/login") {
                    setBody(
                        LoginRequest(
                            username = username.trim(),
                            password = password,
                        ),
                    )
                }.body<LoginResponse>()

                val token = response.token
                val user = response.user
                if (response.success && token != null && user != null) {
                    Result.success(
                        LoginResult(
                            token = token,
                            profile = user.toDomain(),
                        ),
                    )
                } else {
                    Result.failure(IllegalArgumentException(response.message))
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: ClientRequestException) {
                val errorMessage = runCatching {
                    exception.response.body<LoginResponse>().message
                }.getOrNull()
                Result.failure(IllegalArgumentException(errorMessage ?: exception.message))
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }
    }
}

@Serializable
private data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
private data class LoginResponse(
    val success: Boolean,
    val message: String,
    val token: String? = null,
    val user: AuthenticatedUserResponse? = null,
)

@Serializable
private data class AuthenticatedUserResponse(
    val id: Long,
    val username: String,
    val fullName: String,
    val position: String,
)

private fun AuthenticatedUserResponse.toDomain(): LoggedInProfile {
    return LoggedInProfile(
        id = id,
        username = username,
        fullName = fullName,
        position = position,
    )
}
