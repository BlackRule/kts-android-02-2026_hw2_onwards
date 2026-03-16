package com.example.myapplication.feature.shopPicker.repository

import com.example.myapplication.core.cache.CachedShopDao
import com.example.myapplication.core.cache.CachedShopEntity
import com.example.myapplication.feature.shopPicker.model.ShopItem
import com.example.myapplication.feature.shopPicker.model.ShopsPage

internal class RoomShopsCacheDataSource(
    private val cachedShopDao: CachedShopDao,
) : ShopsCacheDataSource {

    override suspend fun getShops(
        query: String,
        page: Int,
        pageSize: Int,
    ): ShopsPage {
        val offset = ((page - 1).coerceAtLeast(0)) * pageSize
        val totalCount = cachedShopDao.getCount(query = query)
        val shops = cachedShopDao.getShops(
            query = query,
            limit = pageSize,
            offset = offset,
        ).map(CachedShopEntity::toDomain)

        return ShopsPage(
            shops = shops,
            page = page,
            pageSize = pageSize,
            totalCount = totalCount,
            hasNextPage = offset + shops.size < totalCount,
        )
    }

    override suspend fun upsertShops(shops: List<ShopItem>) {
        if (shops.isEmpty()) {
            return
        }

        cachedShopDao.upsertShops(shops.map(ShopItem::toEntity))
    }
}

private fun CachedShopEntity.toDomain(): ShopItem {
    return ShopItem(
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

private fun ShopItem.toEntity(): CachedShopEntity {
    return CachedShopEntity(
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
