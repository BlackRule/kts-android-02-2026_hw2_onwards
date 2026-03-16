package com.example.myapplication.feature.shopPicker.repository

import com.example.myapplication.core.network.AppHttpClient
import com.example.myapplication.feature.shopCreation.model.NewShopDraft
import com.example.myapplication.feature.shopPicker.model.ShopItem
import com.example.myapplication.feature.shopPicker.model.ShopsPage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

class ShopsRepository(
    private val httpClient: HttpClient = AppHttpClient.instance,
) {

    suspend fun getShops(
        query: String,
        page: Int,
        pageSize: Int,
    ): Result<ShopsPage> {
        return withContext(Dispatchers.Default) {
            try {
                val response = httpClient.get("/shops") {
                    parameter("query", query.takeIf { it.isNotBlank() })
                    parameter("page", page)
                    parameter("pageSize", pageSize)
                }.body<ShopsListResponse>()

                Result.success(response.toDomain())
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }
    }

    suspend fun createShop(shop: NewShopDraft): Result<ShopItem> {
        return withContext(Dispatchers.Default) {
            try {
                val response = httpClient.post("/shops") {
                    setBody(
                        CreateShopRequest(
                            name = shop.name,
                            city = shop.city,
                            lat = shop.latitude,
                            lon = shop.longitude,
                            address = shop.address,
                        ),
                    )
                }.body<ShopResponse>()

                Result.success(response.toDomain())
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }
    }
}

@Serializable
private data class ShopResponse(
    val id: Long,
    val name: String,
    val city: String? = null,
    val openingTime: String,
    val closingTime: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val address: String? = null,
    val enabled: Boolean,
)

@Serializable
private data class CreateShopRequest(
    val name: String,
    val city: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val address: String? = null,
)

@Serializable
private data class ShopsListResponse(
    val shops: List<ShopResponse>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val hasNextPage: Boolean,
)

private fun ShopsListResponse.toDomain(): ShopsPage {
    return ShopsPage(
        shops = shops.map(ShopResponse::toDomain),
        page = page,
        pageSize = pageSize,
        totalCount = totalCount,
        hasNextPage = hasNextPage,
    )
}

private fun ShopResponse.toDomain(): ShopItem {
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
