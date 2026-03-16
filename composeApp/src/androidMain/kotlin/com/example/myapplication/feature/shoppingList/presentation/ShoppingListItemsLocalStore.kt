package com.example.myapplication.feature.shoppingList.presentation

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.feature.itemCatalog.model.CatalogItem
import com.example.myapplication.feature.itemCatalog.model.UnitType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
actual fun rememberShoppingListItemsLocalStore(): ShoppingListItemsLocalStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        shoppingListItemsLocalStore(context)
    }
}

internal fun shoppingListItemsLocalStore(context: Context): ShoppingListItemsLocalStore {
    return AndroidShoppingListItemsLocalStore.getInstance(context)
}

private class AndroidShoppingListItemsLocalStore private constructor(
    context: Context,
) : ShoppingListItemsLocalStore {
    private val helper = DbHelper(context.applicationContext)

    override suspend fun listRows(ownerKey: Long): List<ShoppingListRowEntity> = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            ROWS_TABLE_NAME,
            ROW_COLUMNS,
            "$COLUMN_OWNER_KEY = ?",
            arrayOf(ownerKey.toString()),
            null,
            null,
            "$COLUMN_ID ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toRowEntity())
                }
            }
        }
    }

    override suspend fun getRow(rowId: Long): ShoppingListRowEntity? = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            ROWS_TABLE_NAME,
            ROW_COLUMNS,
            "$COLUMN_ID = ?",
            arrayOf(rowId.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.toRowEntity()
            } else {
                null
            }
        }
    }

    private suspend fun insertRow(
        ownerKey: Long,
        itemBarcode: String?,
        itemMainName: String,
        pendingItemId: Long?,
        unit: UnitType?,
        price: String,
        discountPercent: String,
        finalPrice: String,
        amount: String,
    ): ShoppingListRowEntity = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COLUMN_OWNER_KEY, ownerKey)
            putNullableString(COLUMN_ITEM_BARCODE, itemBarcode)
            put(COLUMN_ITEM_MAIN_NAME, itemMainName)
            putNullableLong(COLUMN_PENDING_ITEM_ID, pendingItemId)
            putNullableString(COLUMN_UNIT, unit?.name)
            put(COLUMN_PRICE, price)
            put(COLUMN_DISCOUNT_PERCENT, discountPercent)
            put(COLUMN_FINAL_PRICE, finalPrice)
            put(COLUMN_AMOUNT, amount)
        }
        val id = helper.writableDatabase.insertOrThrow(ROWS_TABLE_NAME, null, values)
        ShoppingListRowEntity(
            id = id,
            ownerKey = ownerKey,
            itemBarcode = itemBarcode,
            itemMainName = itemMainName,
            pendingItemId = pendingItemId,
            unit = unit,
            price = price,
            discountPercent = discountPercent,
            finalPrice = finalPrice,
            amount = amount,
        )
    }

    override suspend fun updateRow(row: ShoppingListRowEntity) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COLUMN_OWNER_KEY, row.ownerKey)
            putNullableString(COLUMN_ITEM_BARCODE, row.itemBarcode)
            put(COLUMN_ITEM_MAIN_NAME, row.itemMainName)
            putNullableLong(COLUMN_PENDING_ITEM_ID, row.pendingItemId)
            putNullableString(COLUMN_UNIT, row.unit?.name)
            put(COLUMN_PRICE, row.price)
            put(COLUMN_DISCOUNT_PERCENT, row.discountPercent)
            put(COLUMN_FINAL_PRICE, row.finalPrice)
            put(COLUMN_AMOUNT, row.amount)
        }
        helper.writableDatabase.update(
            ROWS_TABLE_NAME,
            values,
            "$COLUMN_ID = ?",
            arrayOf(row.id.toString()),
        )
        Unit
    }

    private suspend fun deleteRow(rowId: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(
            ROWS_TABLE_NAME,
            "$COLUMN_ID = ?",
            arrayOf(rowId.toString()),
        )
    }

    override suspend fun listPendingItems(): List<PendingItemEntity> = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            PENDING_ITEMS_TABLE_NAME,
            PENDING_ITEM_COLUMNS,
            null,
            null,
            null,
            null,
            "$PENDING_COLUMN_ID ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toPendingItemEntity())
                }
            }
        }
    }

    override suspend fun getPendingItem(id: Long): PendingItemEntity? = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            PENDING_ITEMS_TABLE_NAME,
            PENDING_ITEM_COLUMNS,
            "$PENDING_COLUMN_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.toPendingItemEntity()
            } else {
                null
            }
        }
    }

    override suspend fun createPendingItem(
        mainName: String,
        aliasNames: List<String>,
        unit: UnitType,
        barcodeDraft: String,
    ): PendingItemEntity = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(PENDING_COLUMN_MAIN_NAME, mainName)
            put(PENDING_COLUMN_ALIAS_NAMES_JSON, pendingItemJson.encodeToString(aliasNames))
            put(PENDING_COLUMN_UNIT, unit.name)
            put(PENDING_COLUMN_BARCODE_DRAFT, barcodeDraft)
        }
        val id = helper.writableDatabase.insertOrThrow(PENDING_ITEMS_TABLE_NAME, null, values)
        PendingItemEntity(
            id = id,
            mainName = mainName,
            aliasNames = aliasNames,
            unit = unit,
            barcodeDraft = barcodeDraft,
        )
    }

    override suspend fun updatePendingItem(item: PendingItemEntity) = withContext(Dispatchers.IO) {
        helper.writableDatabase.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(PENDING_COLUMN_MAIN_NAME, item.mainName)
                put(PENDING_COLUMN_ALIAS_NAMES_JSON, pendingItemJson.encodeToString(item.aliasNames))
                put(PENDING_COLUMN_UNIT, item.unit.name)
                put(PENDING_COLUMN_BARCODE_DRAFT, item.barcodeDraft)
            }
            helper.writableDatabase.update(
                PENDING_ITEMS_TABLE_NAME,
                values,
                "$PENDING_COLUMN_ID = ?",
                arrayOf(item.id.toString()),
            )

            val rowValues = ContentValues().apply {
                put(COLUMN_ITEM_MAIN_NAME, item.mainName)
                put(COLUMN_UNIT, item.unit.name)
            }
            helper.writableDatabase.update(
                ROWS_TABLE_NAME,
                rowValues,
                "$COLUMN_PENDING_ITEM_ID = ?",
                arrayOf(item.id.toString()),
            )
            helper.writableDatabase.setTransactionSuccessful()
        } finally {
            helper.writableDatabase.endTransaction()
        }
    }

    override suspend fun assignPendingItemToRow(
        ownerKey: Long,
        rowId: Long?,
        pendingItem: PendingItemEntity,
        price: String,
        discountPercent: String,
        finalPrice: String,
        amount: String,
    ): ShoppingListRowEntity = withContext(Dispatchers.IO) {
        val row = ShoppingListRowEntity(
            id = rowId ?: 0L,
            ownerKey = ownerKey,
            itemBarcode = null,
            itemMainName = pendingItem.mainName,
            pendingItemId = pendingItem.id,
            unit = pendingItem.unit,
            price = price,
            discountPercent = discountPercent,
            finalPrice = finalPrice,
            amount = amount,
        )
        if (rowId == null) {
            insertRow(
                ownerKey = ownerKey,
                itemBarcode = null,
                itemMainName = pendingItem.mainName,
                pendingItemId = pendingItem.id,
                unit = pendingItem.unit,
                price = price,
                discountPercent = discountPercent,
                finalPrice = finalPrice,
                amount = amount,
            )
        } else {
            updateRow(row)
            row
        }
    }

    override suspend fun assignCatalogItemToRow(
        ownerKey: Long,
        rowId: Long?,
        item: CatalogItem,
        price: String,
        discountPercent: String,
        finalPrice: String,
        amount: String,
    ): ShoppingListRowEntity = withContext(Dispatchers.IO) {
        val row = ShoppingListRowEntity(
            id = rowId ?: 0L,
            ownerKey = ownerKey,
            itemBarcode = item.barcode,
            itemMainName = item.mainName,
            pendingItemId = null,
            unit = item.unit,
            price = price,
            discountPercent = discountPercent,
            finalPrice = finalPrice,
            amount = amount,
        )
        if (rowId == null) {
            insertRow(
                ownerKey = ownerKey,
                itemBarcode = item.barcode,
                itemMainName = item.mainName,
                pendingItemId = null,
                unit = item.unit,
                price = price,
                discountPercent = discountPercent,
                finalPrice = finalPrice,
                amount = amount,
            )
        } else {
            updateRow(row)
            row
        }
    }

    override suspend fun resolvePendingItem(
        pendingItemId: Long,
        item: CatalogItem,
    ) = withContext(Dispatchers.IO) {
        helper.writableDatabase.beginTransaction()
        try {
            val rowValues = ContentValues().apply {
                put(COLUMN_ITEM_BARCODE, item.barcode)
                put(COLUMN_ITEM_MAIN_NAME, item.mainName)
                putNull(COLUMN_PENDING_ITEM_ID)
                put(COLUMN_UNIT, item.unit.name)
            }
            helper.writableDatabase.update(
                ROWS_TABLE_NAME,
                rowValues,
                "$COLUMN_PENDING_ITEM_ID = ?",
                arrayOf(pendingItemId.toString()),
            )
            helper.writableDatabase.delete(
                PENDING_ITEMS_TABLE_NAME,
                "$PENDING_COLUMN_ID = ?",
                arrayOf(pendingItemId.toString()),
            )
            helper.writableDatabase.setTransactionSuccessful()
        } finally {
            helper.writableDatabase.endTransaction()
        }
    }

    override suspend fun migrateOwner(fromOwnerKey: Long, toOwnerKey: Long) = withContext(Dispatchers.IO) {
        if (fromOwnerKey == toOwnerKey) {
            return@withContext
        }
        val values = ContentValues().apply {
            put(COLUMN_OWNER_KEY, toOwnerKey)
        }
        helper.writableDatabase.update(
            ROWS_TABLE_NAME,
            values,
            "$COLUMN_OWNER_KEY = ?",
            arrayOf(fromOwnerKey.toString()),
        )
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        helper.writableDatabase.beginTransaction()
        try {
            helper.writableDatabase.delete(ROWS_TABLE_NAME, null, null)
            helper.writableDatabase.delete(PENDING_ITEMS_TABLE_NAME, null, null)
            helper.writableDatabase.setTransactionSuccessful()
        } finally {
            helper.writableDatabase.endTransaction()
        }
    }

    private class DbHelper(context: Context) : SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        DATABASE_VERSION,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            createSchema(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $ROWS_TABLE_NAME")
            db.execSQL("DROP TABLE IF EXISTS $PENDING_ITEMS_TABLE_NAME")
            createSchema(db)
        }

        private fun createSchema(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $ROWS_TABLE_NAME (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_OWNER_KEY INTEGER NOT NULL,
                    $COLUMN_ITEM_BARCODE TEXT,
                    $COLUMN_ITEM_MAIN_NAME TEXT NOT NULL DEFAULT '',
                    $COLUMN_PENDING_ITEM_ID INTEGER,
                    $COLUMN_UNIT TEXT,
                    $COLUMN_PRICE TEXT NOT NULL DEFAULT '',
                    $COLUMN_DISCOUNT_PERCENT TEXT NOT NULL DEFAULT '',
                    $COLUMN_FINAL_PRICE TEXT NOT NULL DEFAULT '',
                    $COLUMN_AMOUNT TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE $PENDING_ITEMS_TABLE_NAME (
                    $PENDING_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $PENDING_COLUMN_MAIN_NAME TEXT NOT NULL DEFAULT '',
                    $PENDING_COLUMN_ALIAS_NAMES_JSON TEXT NOT NULL DEFAULT '[]',
                    $PENDING_COLUMN_UNIT TEXT NOT NULL DEFAULT 'PIECE',
                    $PENDING_COLUMN_BARCODE_DRAFT TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_${ROWS_TABLE_NAME}_$COLUMN_OWNER_KEY ON $ROWS_TABLE_NAME($COLUMN_OWNER_KEY)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_${ROWS_TABLE_NAME}_$COLUMN_PENDING_ITEM_ID ON $ROWS_TABLE_NAME($COLUMN_PENDING_ITEM_ID)",
            )
        }
    }

    companion object {
        private const val DATABASE_NAME = "shopping_list_items.db"
        private const val DATABASE_VERSION = 2

        private const val ROWS_TABLE_NAME = "shopping_list_rows"
        private const val PENDING_ITEMS_TABLE_NAME = "pending_items"

        private const val COLUMN_ID = "id"
        private const val COLUMN_OWNER_KEY = "owner_key"
        private const val COLUMN_ITEM_BARCODE = "item_barcode"
        private const val COLUMN_ITEM_MAIN_NAME = "item_main_name"
        private const val COLUMN_PENDING_ITEM_ID = "pending_item_id"
        private const val COLUMN_UNIT = "unit"
        private const val COLUMN_PRICE = "price"
        private const val COLUMN_DISCOUNT_PERCENT = "discount_percent"
        private const val COLUMN_FINAL_PRICE = "final_price"
        private const val COLUMN_AMOUNT = "amount"

        private const val PENDING_COLUMN_ID = "id"
        private const val PENDING_COLUMN_MAIN_NAME = "main_name"
        private const val PENDING_COLUMN_ALIAS_NAMES_JSON = "alias_names_json"
        private const val PENDING_COLUMN_UNIT = "unit"
        private const val PENDING_COLUMN_BARCODE_DRAFT = "barcode_draft"

        private val ROW_COLUMNS = arrayOf(
            COLUMN_ID,
            COLUMN_OWNER_KEY,
            COLUMN_ITEM_BARCODE,
            COLUMN_ITEM_MAIN_NAME,
            COLUMN_PENDING_ITEM_ID,
            COLUMN_UNIT,
            COLUMN_PRICE,
            COLUMN_DISCOUNT_PERCENT,
            COLUMN_FINAL_PRICE,
            COLUMN_AMOUNT,
        )

        private val PENDING_ITEM_COLUMNS = arrayOf(
            PENDING_COLUMN_ID,
            PENDING_COLUMN_MAIN_NAME,
            PENDING_COLUMN_ALIAS_NAMES_JSON,
            PENDING_COLUMN_UNIT,
            PENDING_COLUMN_BARCODE_DRAFT,
        )

        @Volatile
        private var instance: AndroidShoppingListItemsLocalStore? = null

        fun getInstance(context: Context): AndroidShoppingListItemsLocalStore {
            return instance ?: synchronized(this) {
                instance ?: AndroidShoppingListItemsLocalStore(context).also { created ->
                    instance = created
                }
            }
        }
    }
}

private fun android.database.Cursor.toRowEntity(): ShoppingListRowEntity {
    return ShoppingListRowEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        ownerKey = getLong(getColumnIndexOrThrow("owner_key")),
        itemBarcode = getString(getColumnIndexOrThrow("item_barcode"))?.takeIf(String::isNotBlank),
        itemMainName = getString(getColumnIndexOrThrow("item_main_name")).orEmpty(),
        pendingItemId = getLongOrNull("pending_item_id"),
        unit = getString(getColumnIndexOrThrow("unit"))?.toUnitTypeOrNull(),
        price = getString(getColumnIndexOrThrow("price")).orEmpty(),
        discountPercent = getString(getColumnIndexOrThrow("discount_percent")).orEmpty(),
        finalPrice = getString(getColumnIndexOrThrow("final_price")).orEmpty(),
        amount = getString(getColumnIndexOrThrow("amount")).orEmpty(),
    )
}

private fun android.database.Cursor.toPendingItemEntity(): PendingItemEntity {
    val aliasNamesJson = getString(getColumnIndexOrThrow("alias_names_json")).orEmpty()
    return PendingItemEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        mainName = getString(getColumnIndexOrThrow("main_name")).orEmpty(),
        aliasNames = runCatching {
            pendingItemJson.decodeFromString<List<String>>(aliasNamesJson)
        }.getOrDefault(emptyList()),
        unit = getString(getColumnIndexOrThrow("unit")).toUnitTypeOrNull() ?: UnitType.PIECE,
        barcodeDraft = getString(getColumnIndexOrThrow("barcode_draft")).orEmpty(),
    )
}

private fun android.database.Cursor.getLongOrNull(columnName: String): Long? {
    val columnIndex = getColumnIndexOrThrow(columnName)
    return if (isNull(columnIndex)) {
        null
    } else {
        getLong(columnIndex)
    }
}

private fun ContentValues.putNullableString(
    key: String,
    value: String?,
) {
    if (value == null) {
        putNull(key)
    } else {
        put(key, value)
    }
}

private fun ContentValues.putNullableLong(
    key: String,
    value: Long?,
) {
    if (value == null) {
        putNull(key)
    } else {
        put(key, value)
    }
}

private fun String.toUnitTypeOrNull(): UnitType? {
    return runCatching {
        UnitType.valueOf(trim())
    }.getOrNull()
}

private val pendingItemJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
