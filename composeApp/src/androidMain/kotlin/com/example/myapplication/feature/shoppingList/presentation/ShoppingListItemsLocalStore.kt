package com.example.myapplication.feature.shoppingList.presentation

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ShoppingListItemEntity(
    val id: Long,
    val ownerKey: Long,
    val item: String,
    val price: String,
    val discountPercent: String,
    val finalPrice: String,
    val amount: String,
)

class ShoppingListItemsLocalStore private constructor(
    context: Context,
) {
    private val helper = DbHelper(context.applicationContext)

    suspend fun listItems(ownerKey: Long): List<ShoppingListItemEntity> = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            TABLE_NAME,
            ALL_COLUMNS,
            "$COLUMN_OWNER_KEY = ?",
            arrayOf(ownerKey.toString()),
            null,
            null,
            "$COLUMN_ID ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ShoppingListItemEntity(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                            ownerKey = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_OWNER_KEY)),
                            item = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ITEM)).orEmpty(),
                            price = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PRICE)).orEmpty(),
                            discountPercent = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DISCOUNT_PERCENT)).orEmpty(),
                            finalPrice = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FINAL_PRICE)).orEmpty(),
                            amount = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AMOUNT)).orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    suspend fun addEmptyRow(ownerKey: Long): ShoppingListItemEntity = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COLUMN_OWNER_KEY, ownerKey)
            put(COLUMN_ITEM, "")
            put(COLUMN_PRICE, "")
            put(COLUMN_DISCOUNT_PERCENT, "")
            put(COLUMN_FINAL_PRICE, "")
            put(COLUMN_AMOUNT, "")
        }
        val id = helper.writableDatabase.insertOrThrow(TABLE_NAME, null, values)
        ShoppingListItemEntity(
            id = id,
            ownerKey = ownerKey,
            item = "",
            price = "",
            discountPercent = "",
            finalPrice = "",
            amount = "",
        )
    }

    suspend fun updateItem(item: ShoppingListItemEntity) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COLUMN_OWNER_KEY, item.ownerKey)
            put(COLUMN_ITEM, item.item)
            put(COLUMN_PRICE, item.price)
            put(COLUMN_DISCOUNT_PERCENT, item.discountPercent)
            put(COLUMN_FINAL_PRICE, item.finalPrice)
            put(COLUMN_AMOUNT, item.amount)
        }
        helper.writableDatabase.update(
            TABLE_NAME,
            values,
            "$COLUMN_ID = ?",
            arrayOf(item.id.toString()),
        )
    }

    suspend fun migrateOwner(fromOwnerKey: Long, toOwnerKey: Long) = withContext(Dispatchers.IO) {
        if (fromOwnerKey == toOwnerKey) {
            return@withContext
        }
        val values = ContentValues().apply {
            put(COLUMN_OWNER_KEY, toOwnerKey)
        }
        helper.writableDatabase.update(
            TABLE_NAME,
            values,
            "$COLUMN_OWNER_KEY = ?",
            arrayOf(fromOwnerKey.toString()),
        )
    }

    private class DbHelper(context: Context) : SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        DATABASE_VERSION,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_NAME (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_OWNER_KEY INTEGER NOT NULL,
                    $COLUMN_ITEM TEXT NOT NULL DEFAULT '',
                    $COLUMN_PRICE TEXT NOT NULL DEFAULT '',
                    $COLUMN_DISCOUNT_PERCENT TEXT NOT NULL DEFAULT '',
                    $COLUMN_FINAL_PRICE TEXT NOT NULL DEFAULT '',
                    $COLUMN_AMOUNT TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_${TABLE_NAME}_$COLUMN_OWNER_KEY ON $TABLE_NAME($COLUMN_OWNER_KEY)",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        private const val DATABASE_NAME = "shopping_list_items.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "shopping_list_items"
        private const val COLUMN_ID = "id"
        private const val COLUMN_OWNER_KEY = "owner_key"
        private const val COLUMN_ITEM = "item"
        private const val COLUMN_PRICE = "price"
        private const val COLUMN_DISCOUNT_PERCENT = "discount_percent"
        private const val COLUMN_FINAL_PRICE = "final_price"
        private const val COLUMN_AMOUNT = "amount"

        private val ALL_COLUMNS = arrayOf(
            COLUMN_ID,
            COLUMN_OWNER_KEY,
            COLUMN_ITEM,
            COLUMN_PRICE,
            COLUMN_DISCOUNT_PERCENT,
            COLUMN_FINAL_PRICE,
            COLUMN_AMOUNT,
        )

        @Volatile
        private var instance: ShoppingListItemsLocalStore? = null

        fun getInstance(context: Context): ShoppingListItemsLocalStore {
            return instance ?: synchronized(this) {
                instance ?: ShoppingListItemsLocalStore(context).also { created ->
                    instance = created
                }
            }
        }
    }
}
