package com.example.myapplication.feature.itemCatalog.model

import kotlinx.serialization.Serializable

@Serializable
enum class UnitType {
    PIECE,
    KG,
}

@Serializable
data class CatalogItem(
    val barcode: String,
    val mainName: String,
    val names: List<String>,
    val unit: UnitType,
)

@Serializable
data class HighlightRange(
    val start: Int,
    val endExclusive: Int,
)

@Serializable
data class ItemLookupMatch(
    val item: CatalogItem,
    val matchedName: String,
    val highlightRanges: List<HighlightRange>,
)

@Serializable
data class RetailerPricing(
    val unit: UnitType,
    val price: String,
    val discountPercent: String,
    val finalPrice: String,
)

sealed interface ItemLookupResult {
    data class SingleMatch(
        val item: CatalogItem,
        val normalizedBarcode: String? = null,
        val suggestedAmount: String? = null,
        val retailerPricing: RetailerPricing? = null,
    ) : ItemLookupResult

    data class MultipleMatches(
        val matches: List<ItemLookupMatch>,
    ) : ItemLookupResult

    data class CreateItem(
        val normalizedBarcode: String? = null,
        val suggestedAmount: String? = null,
        val suggestedNames: List<String> = emptyList(),
        val suggestedUnit: UnitType? = null,
        val retailerPricing: RetailerPricing? = null,
    ) : ItemLookupResult
}

data class CreateCatalogItemDraft(
    val barcode: String,
    val mainName: String,
    val aliasNames: List<String>,
    val unit: UnitType,
)

data class PriceObservationImportRow(
    val itemBarcode: String,
    val price: String,
    val discountPercent: String,
    val finalPrice: String,
)

data class PriceObservationImportResult(
    val insertedCount: Int,
    val skippedCount: Int,
)
