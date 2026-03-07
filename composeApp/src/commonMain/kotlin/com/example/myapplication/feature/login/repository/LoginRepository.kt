package com.example.myapplication.feature.login.repository

import com.example.myapplication.DEFAULT_LOGIN_PASSWORD
import com.example.myapplication.DEFAULT_LOGIN_USERNAME

class LoginRepository {

    fun login(
        username: String,
        password: String,
    ): Result<Unit> {
        return if (username == VALID_USERNAME && password == VALID_PASSWORD) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalArgumentException("Invalid username or password"))
        }
    }

    private companion object {
        private const val VALID_USERNAME = DEFAULT_LOGIN_USERNAME
        private const val VALID_PASSWORD = DEFAULT_LOGIN_PASSWORD
    }
}
