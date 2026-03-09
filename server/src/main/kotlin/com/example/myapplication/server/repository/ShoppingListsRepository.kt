package com.example.myapplication.server.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class ShoppingListEntity(
    val id: Long,
    val shopId: Long,
    val paidAt: String,
    val totalAmountMinor: Long,
)

data class UpsertShoppingListEntity(
    val shopId: Long,
    val paidAt: String,
    val totalAmountMinor: Long,
)

data class ShoppingListWithShopEntity(
    val id: Long,
    val shop: ShopEntity,
    val paidAt: String,
    val totalAmountMinor: Long,
)

interface ShoppingListsRepository {
    fun getList(): Result<List<ShoppingListWithShopEntity>>

    fun getById(id: Long): Result<ShoppingListWithShopEntity?>

    fun create(shoppingList: UpsertShoppingListEntity): Result<ShoppingListWithShopEntity>

    fun update(
        id: Long,
        shoppingList: UpsertShoppingListEntity,
    ): Result<ShoppingListWithShopEntity?>

    fun delete(id: Long): Result<Boolean>
}

class JsonShoppingListsRepository(
    private val shopsRepository: ShopsRepository,
    private val storagePath: String = defaultShoppingListsStoragePath(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
    },
) : ShoppingListsRepository {

    private val storageFile: Path = Paths.get(storagePath)
    private val lock = Any()
    private var cachedShoppingLists: MutableList<StoredShoppingListEntity>? = null

    override fun getList(): Result<List<ShoppingListWithShopEntity>> {
        return runCatching {
            synchronized(lock) {
                loadShoppingListsLocked()
                    .sortedWith(
                        compareByDescending<StoredShoppingListEntity> { it.paidAt }
                            .thenByDescending { it.id },
                    )
                    .map(::resolveShoppingListWithShop)
            }
        }
    }

    override fun getById(id: Long): Result<ShoppingListWithShopEntity?> {
        return runCatching {
            synchronized(lock) {
                loadShoppingListsLocked()
                    .firstOrNull { it.id == id }
                    ?.let(::resolveShoppingListWithShop)
            }
        }
    }

    override fun create(shoppingList: UpsertShoppingListEntity): Result<ShoppingListWithShopEntity> {
        return runCatching {
            synchronized(lock) {
                val validated = validateShoppingList(shoppingList)
                val shoppingLists = loadShoppingListsLocked()
                val createdShoppingList = StoredShoppingListEntity(
                    id = (shoppingLists.maxOfOrNull { it.id } ?: 0L) + 1L,
                    shopId = validated.shop.id,
                    paidAt = validated.paidAt,
                    totalAmountMinor = validated.totalAmountMinor,
                )
                shoppingLists.add(createdShoppingList)
                persistShoppingListsLocked(shoppingLists)
                createdShoppingList.toEntity(validated.shop)
            }
        }
    }

    override fun update(
        id: Long,
        shoppingList: UpsertShoppingListEntity,
    ): Result<ShoppingListWithShopEntity?> {
        return runCatching {
            synchronized(lock) {
                val shoppingLists = loadShoppingListsLocked()
                val shoppingListIndex = shoppingLists.indexOfFirst { it.id == id }
                if (shoppingListIndex == -1) {
                    return@runCatching null
                }

                val validated = validateShoppingList(shoppingList)
                val updatedShoppingList = StoredShoppingListEntity(
                    id = id,
                    shopId = validated.shop.id,
                    paidAt = validated.paidAt,
                    totalAmountMinor = validated.totalAmountMinor,
                )
                shoppingLists[shoppingListIndex] = updatedShoppingList
                persistShoppingListsLocked(shoppingLists)
                updatedShoppingList.toEntity(validated.shop)
            }
        }
    }

    override fun delete(id: Long): Result<Boolean> {
        return runCatching {
            synchronized(lock) {
                val shoppingLists = loadShoppingListsLocked()
                val removed = shoppingLists.removeAll { it.id == id }
                if (removed) {
                    persistShoppingListsLocked(shoppingLists)
                }
                removed
            }
        }
    }

    private fun loadShoppingListsLocked(): MutableList<StoredShoppingListEntity> {
        cachedShoppingLists?.let { return it }

        ensureStorageFileExists()
        val loadedShoppingLists = Files.readString(storageFile, StandardCharsets.UTF_8)
            .let { json.decodeFromString<List<StoredShoppingListEntity>>(it) }
            .toMutableList()
        cachedShoppingLists = loadedShoppingLists
        return loadedShoppingLists
    }

    private fun persistShoppingListsLocked(shoppingLists: List<StoredShoppingListEntity>) {
        storageFile.parent?.let(Files::createDirectories)
        Files.writeString(
            storageFile,
            json.encodeToString(shoppingLists),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    private fun ensureStorageFileExists() {
        if (Files.exists(storageFile)) {
            return
        }

        storageFile.parent?.let(Files::createDirectories)
        Files.writeString(
            storageFile,
            "[]",
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        )
    }

    private fun validateShoppingList(shoppingList: UpsertShoppingListEntity): ValidatedShoppingList {
        require(shoppingList.totalAmountMinor >= 0L) {
            "Shopping list total must be zero or greater"
        }

        val normalizedPaidAt = shoppingList.paidAt.trim()
        require(normalizedPaidAt.isNotEmpty()) {
            "Payment time is required"
        }
        parsePaidAt(normalizedPaidAt)

        val shop = shopsRepository.getById(shoppingList.shopId)
            .getOrElse { throw it }
            ?: throw IllegalArgumentException("Selected shop was not found")

        return ValidatedShoppingList(
            shop = shop,
            paidAt = normalizedPaidAt,
            totalAmountMinor = shoppingList.totalAmountMinor,
        )
    }

    private fun resolveShoppingListWithShop(
        shoppingList: StoredShoppingListEntity,
    ): ShoppingListWithShopEntity {
        val shop = shopsRepository.getById(shoppingList.shopId)
            .getOrElse { throw it }
            ?: error("Missing shop ${shoppingList.shopId} for shopping list ${shoppingList.id}")
        return shoppingList.toEntity(shop)
    }

    private fun parsePaidAt(value: String): LocalDateTime {
        return try {
            LocalDateTime.parse(value, PAID_AT_FORMATTER)
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("Payment time must use yyyy-MM-dd HH:mm")
        }
    }

    private companion object {
        private val PAID_AT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        private fun defaultShoppingListsStoragePath(): String {
            return System.getenv("SHOPPING_LISTS_FILE_PATH")
                ?.takeIf(String::isNotBlank)
                ?: Paths.get(System.getProperty("user.dir"), "data", "shopping-lists.json").toString()
        }
    }
}

@Serializable
private data class StoredShoppingListEntity(
    val id: Long,
    val shopId: Long,
    val paidAt: String,
    val totalAmountMinor: Long,
)

private data class ValidatedShoppingList(
    val shop: ShopEntity,
    val paidAt: String,
    val totalAmountMinor: Long,
)

private fun StoredShoppingListEntity.toEntity(shop: ShopEntity): ShoppingListWithShopEntity {
    return ShoppingListWithShopEntity(
        id = id,
        shop = shop,
        paidAt = paidAt,
        totalAmountMinor = totalAmountMinor,
    )
}
