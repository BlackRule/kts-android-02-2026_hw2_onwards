package com.example.myapplication.feature.shoppingLists.repository

import com.example.myapplication.core.network.AppHttpClient
import com.example.myapplication.feature.shopPicker.model.ShopItem
import com.example.myapplication.feature.shoppingLists.model.ShoppingListItem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

class ShoppingListsRepository(
    private val httpClient: HttpClient = AppHttpClient.instance,
) {

    suspend fun getShoppingLists(): Result<List<ShoppingListItem>> {
        return withContext(Dispatchers.Default) {
            try {
                val response = httpClient.get("/shopping-lists").body<ShoppingListsResponse>()
                Result.success(response.shoppingLists.map(ShoppingListResponse::toDomain))
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }
    }

    suspend fun getShoppingList(id: Long): Result<ShoppingListItem> {
        return withContext(Dispatchers.Default) {
            try {
                val response = httpClient.get("/shopping-lists/$id").body<ShoppingListResponse>()
                Result.success(response.toDomain())
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }
    }

    suspend fun createShoppingList(
        shopId: Long,
        paidAt: String,
        totalAmountMinor: Long,
    ): Result<ShoppingListItem> {
        return withContext(Dispatchers.Default) {
            try {
                val response = httpClient.post("/shopping-lists") {
                    setBody(
                        UpsertShoppingListRequest(
                            shopId = shopId,
                            paidAt = paidAt,
                            totalAmountMinor = totalAmountMinor,
                        ),
                    )
                }.body<ShoppingListResponse>()
                Result.success(response.toDomain())
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }
    }

    suspend fun updateShoppingList(
        id: Long,
        shopId: Long,
        paidAt: String,
        totalAmountMinor: Long,
    ): Result<ShoppingListItem> {
        return withContext(Dispatchers.Default) {
            try {
                val response = httpClient.put("/shopping-lists/$id") {
                    setBody(
                        UpsertShoppingListRequest(
                            shopId = shopId,
                            paidAt = paidAt,
                            totalAmountMinor = totalAmountMinor,
                        ),
                    )
                }.body<ShoppingListResponse>()
                Result.success(response.toDomain())
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }
    }

    suspend fun deleteShoppingList(id: Long): Result<Unit> {
        return withContext(Dispatchers.Default) {
            try {
                httpClient.delete("/shopping-lists/$id")
                Result.success(Unit)
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }
    }
}

@Serializable
private data class ShoppingListsResponse(
    val shoppingLists: List<ShoppingListResponse>,
)

@Serializable
private data class ShoppingListResponse(
    val id: Long,
    val shop: ShopResponse,
    val paidAt: String,
    val totalAmountMinor: Long,
)

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
private data class UpsertShoppingListRequest(
    val shopId: Long,
    val paidAt: String,
    val totalAmountMinor: Long,
)

private fun ShoppingListResponse.toDomain(): ShoppingListItem {
    return ShoppingListItem(
        id = id,
        shop = shop.toDomain(),
        paidAt = paidAt,
        totalAmountMinor = totalAmountMinor,
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
