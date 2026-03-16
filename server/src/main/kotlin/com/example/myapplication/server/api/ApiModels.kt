package com.example.myapplication.server.api

import kotlinx.serialization.Serializable

@Serializable
enum class UnitResponse {
    PIECE,
    KG,
}

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val token: String? = null,
    val user: AuthenticatedUserResponse? = null,
)

@Serializable
data class AuthenticatedUserResponse(
    val id: Long,
    val username: String,
    val fullName: String,
    val position: String,
)

@Serializable
data class UserResponse(
    val id: Long,
    val fullName: String,
    val position: String,
)

@Serializable
data class UsersListResponse(
    val users: List<UserResponse>,
)

@Serializable
data class ShopResponse(
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
data class CreateShopRequest(
    val name: String,
    val city: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val address: String? = null,
)

@Serializable
data class ShopsListResponse(
    val shops: List<ShopResponse>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val hasNextPage: Boolean,
)

@Serializable
data class ShoppingListResponse(
    val id: Long,
    val shop: ShopResponse,
    val paidAt: String,
    val totalAmountMinor: Long,
)

@Serializable
data class ShoppingListsListResponse(
    val shoppingLists: List<ShoppingListResponse>,
)

@Serializable
data class UpsertShoppingListRequest(
    val shopId: Long,
    val paidAt: String,
    val totalAmountMinor: Long,
)

@Serializable
data class ItemLookupRequest(
    val query: String,
    val soldByWeight: Boolean = false,
)

@Serializable
data class ItemHighlightRangeResponse(
    val start: Int,
    val endExclusive: Int,
)

@Serializable
data class ItemSummaryResponse(
    val barcode: String,
    val mainName: String,
    val names: List<String>,
    val unit: UnitResponse,
)

@Serializable
data class ItemLookupMatchResponse(
    val item: ItemSummaryResponse,
    val matchedName: String,
    val highlightRanges: List<ItemHighlightRangeResponse>,
)

@Serializable
data class RetailerLookupResponse(
    val unit: UnitResponse,
    val price: String,
    val discountPercent: String,
    val finalPrice: String,
)

@Serializable
data class ItemLookupResponse(
    val kind: String,
    val item: ItemSummaryResponse? = null,
    val matches: List<ItemLookupMatchResponse> = emptyList(),
    val normalizedBarcode: String? = null,
    val suggestedAmount: String? = null,
    val suggestedNames: List<String> = emptyList(),
    val suggestedUnit: UnitResponse? = null,
    val retailer: RetailerLookupResponse? = null,
)

@Serializable
data class CreateItemRequest(
    val barcode: String,
    val mainName: String,
    val aliasNames: List<String>,
    val unit: UnitResponse,
)

@Serializable
data class PriceObservationImportRowRequest(
    val itemBarcode: String,
    val price: String,
    val discountPercent: String,
    val finalPrice: String,
)

@Serializable
data class PriceObservationImportRequest(
    val shopId: Long,
    val paymentTime: String,
    val rows: List<PriceObservationImportRowRequest>,
)

@Serializable
data class PriceObservationImportResponse(
    val insertedCount: Int,
    val skippedCount: Int,
)

@Serializable
data class ErrorResponse(
    val message: String,
)
