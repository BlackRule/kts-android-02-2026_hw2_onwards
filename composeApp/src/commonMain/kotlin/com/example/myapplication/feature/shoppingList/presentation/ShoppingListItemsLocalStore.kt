package com.example.myapplication.feature.shoppingList.presentation

import androidx.compose.runtime.Composable
import com.example.myapplication.feature.itemCatalog.model.CatalogItem
import com.example.myapplication.feature.itemCatalog.model.UnitType

data class ShoppingListRowEntity(
    val id: Long,
    val ownerKey: Long,
    val itemBarcode: String? = null,
    val itemMainName: String,
    val pendingItemId: Long? = null,
    val unit: UnitType? = null,
    val price: String,
    val discountPercent: String,
    val finalPrice: String,
    val amount: String,
)

data class PendingItemEntity(
    val id: Long,
    val mainName: String,
    val aliasNames: List<String>,
    val unit: UnitType,
    val barcodeDraft: String,
)

interface ShoppingListItemsLocalStore {
    suspend fun listRows(ownerKey: Long): List<ShoppingListRowEntity>

    suspend fun getRow(rowId: Long): ShoppingListRowEntity?

    suspend fun updateRow(row: ShoppingListRowEntity)

    suspend fun listPendingItems(): List<PendingItemEntity>

    suspend fun getPendingItem(id: Long): PendingItemEntity?

    suspend fun createPendingItem(
        mainName: String,
        aliasNames: List<String>,
        unit: UnitType,
        barcodeDraft: String,
    ): PendingItemEntity

    suspend fun updatePendingItem(item: PendingItemEntity)

    suspend fun assignPendingItemToRow(
        ownerKey: Long,
        rowId: Long?,
        pendingItem: PendingItemEntity,
        price: String,
        discountPercent: String,
        finalPrice: String,
        amount: String,
    ): ShoppingListRowEntity

    suspend fun assignCatalogItemToRow(
        ownerKey: Long,
        rowId: Long?,
        item: CatalogItem,
        price: String,
        discountPercent: String,
        finalPrice: String,
        amount: String,
    ): ShoppingListRowEntity

    suspend fun resolvePendingItem(
        pendingItemId: Long,
        item: CatalogItem,
    )

    suspend fun migrateOwner(
        fromOwnerKey: Long,
        toOwnerKey: Long,
    )

    suspend fun clearAll()
}

@Composable
expect fun rememberShoppingListItemsLocalStore(): ShoppingListItemsLocalStore
