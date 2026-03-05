package com.example.myapplication.feature.main.repository

import com.example.myapplication.feature.main.model.UserItem

class UsersRepository {

    fun getList(): List<UserItem> {
        return listOf(
            UserItem(id = 1, fullName = "Sophia Turner", position = "Product Manager"),
            UserItem(id = 2, fullName = "Liam Johnson", position = "Android Engineer"),
            UserItem(id = 3, fullName = "Olivia Brown", position = "QA Engineer"),
            UserItem(id = 4, fullName = "Noah Davis", position = "UX Designer"),
            UserItem(id = 5, fullName = "Emma Wilson", position = "Backend Engineer"),
            UserItem(id = 6, fullName = "James Miller", position = "Data Analyst"),
        )
    }
}
