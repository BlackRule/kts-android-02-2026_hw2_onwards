package com.example.myapplication.feature.itemCatalog.repository

import com.example.myapplication.core.network.AppHttpClient
import com.example.myapplication.feature.itemCatalog.model.CatalogItem
import com.example.myapplication.feature.itemCatalog.model.CreateCatalogItemDraft
import com.example.myapplication.feature.itemCatalog.model.HighlightRange
import com.example.myapplication.feature.itemCatalog.model.ItemLookupMatch
import com.example.myapplication.feature.itemCatalog.model.ItemLookupResult
import com.example.myapplication.feature.itemCatalog.model.PriceObservationImportResult
import com.example.myapplication.feature.itemCatalog.model.PriceObservationImportRow
import com.example.myapplication.feature.itemCatalog.model.RetailerPricing
import com.example.myapplication.feature.itemCatalog.model.UnitType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

class ItemCatalogRepository(
    private val httpClient: HttpClient = AppHttpClient.instance,
) {

    suspend fun lookupItem(
        query: String,
        soldByWeight: Boolean = false,
    ): Result<ItemLookupResult> {
        return withContext(Dispatchers.Default) {
            try {
                val response = httpClient.post("/items/lookup") {
                    setBody(
                        ItemLookupRequest(
                            query = query,
                            soldByWeight = soldByWeight,
                        ),
                    )
                }.body<ItemLookupResponse>()
                Result.success(response.toDomain())
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }
    }

    suspend fun createItem(draft: CreateCatalogItemDraft): Result<CatalogItem> {
        return withContext(Dispatchers.Default) {
            try {
                val response = httpClient.post("/items") {
                    setBody(
                        CreateItemRequest(
                            barcode = draft.barcode,
                            mainName = draft.mainName,
                            aliasNames = draft.aliasNames,
                            unit = draft.unit,
                        ),
                    )
                }.body<ItemSummaryResponse>()
                Result.success(response.toDomain())
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }
    }

    suspend fun importPriceObservations(
        shopId: Long,
        paymentTime: String,
        rows: List<PriceObservationImportRow>,
    ): Result<PriceObservationImportResult> {
        return withContext(Dispatchers.Default) {
            try {
                val response = httpClient.post("/price-observations/import") {
                    setBody(
                        PriceObservationImportRequest(
                            shopId = shopId,
                            paymentTime = paymentTime,
                            rows = rows.map { row ->
                                PriceObservationImportRowRequest(
                                    itemBarcode = row.itemBarcode,
                                    price = row.price,
                                    discountPercent = row.discountPercent,
                                    finalPrice = row.finalPrice,
                                )
                            },
                        ),
                    )
                }.body<PriceObservationImportResponse>()
                Result.success(
                    PriceObservationImportResult(
                        insertedCount = response.insertedCount,
                        skippedCount = response.skippedCount,
                    ),
                )
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }
    }
}

@Serializable
private data class ItemLookupRequest(
    val query: String,
    val soldByWeight: Boolean = false,
)

@Serializable
private data class ItemLookupResponse(
    val kind: String,
    val item: ItemSummaryResponse? = null,
    val matches: List<ItemLookupMatchResponse> = emptyList(),
    val normalizedBarcode: String? = null,
    val suggestedAmount: String? = null,
    val suggestedNames: List<String> = emptyList(),
    val suggestedUnit: UnitType? = null,
    val retailer: RetailerLookupResponse? = null,
)

@Serializable
private data class ItemSummaryResponse(
    val barcode: String,
    val mainName: String,
    val names: List<String>,
    val unit: UnitType,
)

@Serializable
private data class ItemLookupMatchResponse(
    val item: ItemSummaryResponse,
    val matchedName: String,
    val highlightRanges: List<HighlightRangeResponse>,
)

@Serializable
private data class HighlightRangeResponse(
    val start: Int,
    val endExclusive: Int,
)

@Serializable
private data class RetailerLookupResponse(
    val unit: UnitType,
    val price: String,
    val discountPercent: String,
    val finalPrice: String,
)

@Serializable
private data class CreateItemRequest(
    val barcode: String,
    val mainName: String,
    val aliasNames: List<String>,
    val unit: UnitType,
)

@Serializable
private data class PriceObservationImportRequest(
    val shopId: Long,
    val paymentTime: String,
    val rows: List<PriceObservationImportRowRequest>,
)

@Serializable
private data class PriceObservationImportRowRequest(
    val itemBarcode: String,
    val price: String,
    val discountPercent: String,
    val finalPrice: String,
)

@Serializable
private data class PriceObservationImportResponse(
    val insertedCount: Int,
    val skippedCount: Int,
)

private fun ItemLookupResponse.toDomain(): ItemLookupResult {
    return when (kind) {
        "single_match" -> ItemLookupResult.SingleMatch(
            item = requireNotNull(item) { "Missing item payload" }.toDomain(),
            normalizedBarcode = normalizedBarcode,
            suggestedAmount = suggestedAmount,
            retailerPricing = retailer?.toDomain(),
        )

        "multiple_matches" -> ItemLookupResult.MultipleMatches(
            matches = matches.map(ItemLookupMatchResponse::toDomain),
        )

        "create_item" -> ItemLookupResult.CreateItem(
            normalizedBarcode = normalizedBarcode,
            suggestedAmount = suggestedAmount,
            suggestedNames = suggestedNames,
            suggestedUnit = suggestedUnit,
            retailerPricing = retailer?.toDomain(),
        )

        else -> error("Unknown item lookup kind: $kind")
    }
}

private fun ItemSummaryResponse.toDomain(): CatalogItem {
    return CatalogItem(
        barcode = barcode,
        mainName = mainName,
        names = names,
        unit = unit,
    )
}

private fun ItemLookupMatchResponse.toDomain(): ItemLookupMatch {
    return ItemLookupMatch(
        item = item.toDomain(),
        matchedName = matchedName,
        highlightRanges = highlightRanges.map(HighlightRangeResponse::toDomain),
    )
}

private fun HighlightRangeResponse.toDomain(): HighlightRange {
    return HighlightRange(
        start = start,
        endExclusive = endExclusive,
    )
}

private fun RetailerLookupResponse.toDomain(): RetailerPricing {
    return RetailerPricing(
        unit = unit,
        price = price,
        discountPercent = discountPercent,
        finalPrice = finalPrice,
    )
}
