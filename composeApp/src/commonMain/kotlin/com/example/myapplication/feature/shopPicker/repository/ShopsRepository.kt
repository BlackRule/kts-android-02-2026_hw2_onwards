package com.example.myapplication.feature.shopPicker.repository

import com.example.myapplication.core.network.AppHttpClient
import com.example.myapplication.feature.shopPicker.model.ShopItem
import com.example.myapplication.feature.shopPicker.model.ShopsPage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
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
}

@Serializable
private data class ShopResponse(
    val id: Long,
    val name: String,
    val city: String,
    val openingTime: String,
    val closingTime: String,
    val lat: Double,
    val lon: Double,
    val address: String,
    val enabled: Boolean,
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
        shops = shops.map { shop ->
            ShopItem(
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
        page = page,
        pageSize = pageSize,
        totalCount = totalCount,
        hasNextPage = hasNextPage,
    )
}
