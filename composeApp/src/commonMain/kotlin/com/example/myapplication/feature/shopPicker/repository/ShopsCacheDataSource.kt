package com.example.myapplication.feature.shopPicker.repository

import com.example.myapplication.feature.shopPicker.model.ShopItem
import com.example.myapplication.feature.shopPicker.model.ShopsPage

interface ShopsCacheDataSource {
    suspend fun getShops(
        query: String,
        page: Int,
        pageSize: Int,
    ): ShopsPage

    suspend fun upsertShops(shops: List<ShopItem>)
}

object NoOpShopsCacheDataSource : ShopsCacheDataSource {
    override suspend fun getShops(
        query: String,
        page: Int,
        pageSize: Int,
    ): ShopsPage {
        return ShopsPage(
            shops = emptyList(),
            page = page,
            pageSize = pageSize,
            totalCount = 0,
            hasNextPage = false,
        )
    }

    override suspend fun upsertShops(shops: List<ShopItem>) = Unit
}
