package com.example.myapplication.server.repository

import com.example.myapplication.server.data.DatabaseFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.Properties

enum class UnitCode {
    PIECE,
    KG,
}

data class CatalogItemEntity(
    val barcode: String,
    val mainName: String,
    val names: List<String>,
    val unit: UnitCode,
)

data class HighlightRangeEntity(
    val start: Int,
    val endExclusive: Int,
)

data class ItemLookupMatchEntity(
    val item: CatalogItemEntity,
    val matchedName: String,
    val highlightRanges: List<HighlightRangeEntity>,
)

data class RetailerPriceEntity(
    val unit: UnitCode,
    val price: String,
    val discountPercent: String,
    val finalPrice: String,
)

sealed interface ItemLookupEntity {
    data class Single(
        val item: CatalogItemEntity,
        val normalizedBarcode: String? = null,
        val suggestedAmount: String? = null,
        val retailerPrice: RetailerPriceEntity? = null,
    ) : ItemLookupEntity

    data class Multiple(
        val matches: List<ItemLookupMatchEntity>,
    ) : ItemLookupEntity

    data class Create(
        val normalizedBarcode: String? = null,
        val suggestedAmount: String? = null,
        val suggestedNames: List<String> = emptyList(),
        val suggestedUnit: UnitCode? = null,
        val retailerPrice: RetailerPriceEntity? = null,
    ) : ItemLookupEntity
}

data class CreateCatalogItemEntity(
    val barcode: String,
    val mainName: String,
    val aliasNames: List<String>,
    val unit: UnitCode,
)

data class PriceObservationImportRowEntity(
    val itemBarcode: String,
    val price: String,
    val discountPercent: String,
    val finalPrice: String,
)

data class PriceObservationImportResultEntity(
    val insertedCount: Int,
    val skippedCount: Int,
)

interface ItemCatalogService {
    fun lookup(
        query: String,
        soldByWeight: Boolean = false,
    ): Result<ItemLookupEntity>

    fun createItem(item: CreateCatalogItemEntity): Result<CatalogItemEntity>

    fun importPriceObservations(
        shopId: Long,
        paymentTime: String,
        rows: List<PriceObservationImportRowEntity>,
    ): Result<PriceObservationImportResultEntity>
}

class PostgresItemCatalogService internal constructor(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    },
) : ItemCatalogService {
    private val externalLookupService = ExternalLookupService()

    override fun lookup(
        query: String,
        soldByWeight: Boolean,
    ): Result<ItemLookupEntity> {
        return runCatching {
            val normalizedQuery = query.trim()
            require(normalizedQuery.isNotEmpty()) {
                "Query is required"
            }

            val barcodeCandidate = normalizedQuery.takeIf(::looksLikeBarcode)
            val weightedBarcode = barcodeCandidate
                ?.takeIf { soldByWeight }
                ?.toWeightedBarcodeSuggestion()
            val externalSuggestion = barcodeCandidate?.let { barcode ->
                externalLookupService.lookup(
                    barcode = barcode,
                    soldByWeight = soldByWeight,
                )
            }
            val effectiveBarcode = externalSuggestion?.normalizedBarcode
                ?: weightedBarcode?.normalizedBarcode
                ?: barcodeCandidate
            val suggestedAmount = externalSuggestion?.suggestedAmount ?: weightedBarcode?.suggestedAmount

            DatabaseFactory.getConnection().use { connection ->
                effectiveBarcode?.let { barcode ->
                    loadItemByBarcode(connection, barcode)?.let { item ->
                        return@runCatching ItemLookupEntity.Single(
                            item = item,
                            normalizedBarcode = effectiveBarcode,
                            suggestedAmount = suggestedAmount,
                            retailerPrice = externalSuggestion?.retailerPrice,
                        )
                    }
                }

                val matches = searchItems(normalizedQuery, loadAllItems(connection))
                when {
                    matches.size == 1 -> ItemLookupEntity.Single(
                        item = matches.single().item,
                        normalizedBarcode = effectiveBarcode,
                        suggestedAmount = suggestedAmount,
                        retailerPrice = externalSuggestion?.retailerPrice,
                    )

                    matches.isNotEmpty() -> ItemLookupEntity.Multiple(matches)

                    else -> ItemLookupEntity.Create(
                        normalizedBarcode = effectiveBarcode,
                        suggestedAmount = suggestedAmount,
                        suggestedNames = externalSuggestion?.suggestedNames ?: normalizedQuery.asFallbackSuggestedNames(),
                        suggestedUnit = externalSuggestion?.suggestedUnit,
                        retailerPrice = externalSuggestion?.retailerPrice,
                    )
                }
            }
        }
    }

    override fun createItem(item: CreateCatalogItemEntity): Result<CatalogItemEntity> {
        return runCatching {
            val normalizedBarcode = item.barcode.trim()
            require(normalizedBarcode.isNotEmpty()) {
                "Barcode is required"
            }

            val normalizedMainName = item.mainName.trim()
            require(normalizedMainName.isNotEmpty()) {
                "Main name is required"
            }

            val normalizedNames = linkedSetOf(normalizedMainName).apply {
                item.aliasNames
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .forEach(::add)
            }.toList()

            DatabaseFactory.getConnection().use { connection ->
                require(loadItemByBarcode(connection, normalizedBarcode) == null) {
                    "Item with barcode $normalizedBarcode already exists"
                }

                connection.autoCommit = false
                try {
                    connection.prepareStatement(
                        """
                        INSERT INTO items(barcode, main_name, unit_code)
                        VALUES (?, ?, ?)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, normalizedBarcode)
                        statement.setString(2, normalizedMainName)
                        statement.setString(3, item.unit.name)
                        statement.executeUpdate()
                    }

                    connection.prepareStatement(
                        """
                        INSERT INTO item_names(item_barcode, name)
                        VALUES (?, ?)
                        ON CONFLICT (item_barcode, name) DO NOTHING
                        """.trimIndent(),
                    ).use { statement ->
                        normalizedNames.forEach { name ->
                            statement.setString(1, normalizedBarcode)
                            statement.setString(2, name)
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }

                    connection.commit()
                } catch (exception: Exception) {
                    connection.rollback()
                    throw exception
                } finally {
                    connection.autoCommit = true
                }

                loadItemByBarcode(connection, normalizedBarcode)
                    ?: error("Failed to load created item $normalizedBarcode")
            }
        }
    }

    override fun importPriceObservations(
        shopId: Long,
        paymentTime: String,
        rows: List<PriceObservationImportRowEntity>,
    ): Result<PriceObservationImportResultEntity> {
        return runCatching {
            val parsedPaymentTime = parsePaymentTime(paymentTime.trim())
            require(rows.isNotEmpty()) {
                "At least one row is required"
            }

            DatabaseFactory.getConnection().use { connection ->
                require(shopExists(connection, shopId)) {
                    "Selected shop was not found"
                }

                connection.autoCommit = false
                try {
                    var insertedCount = 0
                    var skippedCount = 0

                    connection.prepareStatement(
                        """
                        INSERT INTO price_observations(
                            shop_id,
                            item_barcode,
                            price,
                            discount_percent,
                            final_price,
                            payment_time
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (shop_id, item_barcode, price, final_price) DO NOTHING
                        """.trimIndent(),
                    ).use { statement ->
                        rows.forEach { row ->
                            val normalizedBarcode = row.itemBarcode.trim()
                            require(normalizedBarcode.isNotEmpty()) {
                                "Item barcode is required"
                            }
                            require(loadItemByBarcode(connection, normalizedBarcode) != null) {
                                "Item $normalizedBarcode was not found"
                            }

                            val price = parseMoney(row.price, "price")
                            val discountPercent = parseDiscount(row.discountPercent)
                            val finalPrice = parseMoney(row.finalPrice, "final_price")

                            statement.setLong(1, shopId)
                            statement.setString(2, normalizedBarcode)
                            statement.setBigDecimal(3, price)
                            statement.setBigDecimal(4, discountPercent)
                            statement.setBigDecimal(5, finalPrice)
                            statement.setTimestamp(6, Timestamp.valueOf(parsedPaymentTime))

                            if (statement.executeUpdate() == 1) {
                                insertedCount += 1
                            } else {
                                skippedCount += 1
                            }
                        }
                    }

                    connection.commit()
                    PriceObservationImportResultEntity(
                        insertedCount = insertedCount,
                        skippedCount = skippedCount,
                    )
                } catch (exception: Exception) {
                    connection.rollback()
                    throw exception
                } finally {
                    connection.autoCommit = true
                }
            }
        }
    }

    private fun loadItemByBarcode(
        connection: Connection,
        barcode: String,
    ): CatalogItemEntity? {
        return connection.prepareStatement(
            """
            SELECT i.barcode, i.main_name, i.unit_code, n.name
            FROM items i
            LEFT JOIN item_names n ON n.item_barcode = i.barcode
            WHERE i.barcode = ?
            ORDER BY n.name ASC
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, barcode)
            statement.executeQuery().use { resultSet ->
                resultSet.toCatalogItems().singleOrNull()
            }
        }
    }

    private fun loadAllItems(connection: Connection): List<CatalogItemEntity> {
        return connection.prepareStatement(
            """
            SELECT i.barcode, i.main_name, i.unit_code, n.name
            FROM items i
            LEFT JOIN item_names n ON n.item_barcode = i.barcode
            ORDER BY i.main_name ASC, n.name ASC
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { resultSet ->
                resultSet.toCatalogItems()
            }
        }
    }

    private fun shopExists(
        connection: Connection,
        shopId: Long,
    ): Boolean {
        return connection.prepareStatement(
            """
            SELECT EXISTS(SELECT 1 FROM shops WHERE id = ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, shopId)
            statement.executeQuery().use { resultSet ->
                resultSet.next()
                resultSet.getBoolean(1)
            }
        }
    }

    private fun searchItems(
        query: String,
        items: List<CatalogItemEntity>,
    ): List<ItemLookupMatchEntity> {
        return items.mapNotNull { item ->
            item.bestMatchFor(query)
        }.sortedWith(
            compareByDescending<ScoredItemLookupMatch> { it.score }
                .thenBy { it.match.item.mainName.lowercase(Locale.getDefault()) },
        ).map(ScoredItemLookupMatch::match)
    }

    private fun CatalogItemEntity.bestMatchFor(query: String): ScoredItemLookupMatch? {
        val normalizedQuery = normalizeForSearch(query)
        if (normalizedQuery.isBlank()) {
            return null
        }

        val barcodeQuery = query.filter(Char::isDigit)
        val exactBarcodeScore = when {
            barcodeQuery.isBlank() -> 0
            barcode == barcodeQuery -> 10_000
            barcode.startsWith(barcodeQuery) -> 7_500
            else -> 0
        }

        val bestNameMatch = names.mapNotNull { candidateName ->
            candidateName.scoreAgainst(query)
        }.maxByOrNull { it.score }

        val candidate = when {
            exactBarcodeScore > 0 && bestNameMatch == null -> NameMatchScore(
                matchedName = mainName,
                highlightRanges = listOf(HighlightRangeEntity(0, mainName.length)),
                score = exactBarcodeScore,
            )

            bestNameMatch != null && exactBarcodeScore > bestNameMatch.score -> NameMatchScore(
                matchedName = mainName,
                highlightRanges = listOf(HighlightRangeEntity(0, mainName.length)),
                score = exactBarcodeScore,
            )

            else -> bestNameMatch
        } ?: return null

        return ScoredItemLookupMatch(
            match = ItemLookupMatchEntity(
                item = this,
                matchedName = candidate.matchedName,
                highlightRanges = candidate.highlightRanges,
            ),
            score = candidate.score,
        )
    }

    private fun String.scoreAgainst(query: String): NameMatchScore? {
        val normalizedCandidate = normalizeForSearch(this)
        val normalizedQuery = normalizeForSearch(query)
        if (normalizedCandidate.isBlank() || normalizedQuery.isBlank()) {
            return null
        }

        val candidateTokens = normalizedCandidate.split(' ').filter(String::isNotBlank)
        val queryTokens = normalizedQuery.split(' ').filter(String::isNotBlank)
        val highlightRanges = buildHighlightRanges(this, queryTokens)

        var score = when {
            normalizedCandidate == normalizedQuery -> 6_000
            normalizedCandidate.startsWith(normalizedQuery) -> 5_000
            normalizedCandidate.contains(normalizedQuery) -> 4_000
            else -> 0
        }

        val overlappingTokens = queryTokens.count { token ->
            candidateTokens.any { candidateToken ->
                candidateToken.contains(token) || token.contains(candidateToken)
            }
        }
        score += overlappingTokens * 500

        val distance = levenshteinDistance(normalizedCandidate, normalizedQuery)
        if (distance <= normalizedQuery.length.coerceAtLeast(4) / 3) {
            score += (2_000 - distance * 250).coerceAtLeast(250)
        }

        if (score <= 0) {
            return null
        }

        return NameMatchScore(
            matchedName = this,
            highlightRanges = highlightRanges.ifEmpty {
                listOf(HighlightRangeEntity(0, this.length))
            },
            score = score,
        )
    }
}

private data class NameMatchScore(
    val matchedName: String,
    val highlightRanges: List<HighlightRangeEntity>,
    val score: Int,
)

private data class ScoredItemLookupMatch(
    val match: ItemLookupMatchEntity,
    val score: Int,
)

private class ExternalLookupService(
    private val config: ExternalCatalogConfig = ExternalCatalogConfig.load(),
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    },
) {
    fun lookup(
        barcode: String,
        soldByWeight: Boolean = false,
    ): ExternalLookupSuggestion? {
        if (!looksLikeBarcode(barcode)) {
            return null
        }

        if (soldByWeight) {
            return lookupSoldByWeightProduct(barcode)
        }

        val fullResult = lookupRetailerProduct(barcode)
        if (fullResult is RetailerLookupOutcome.Found) {
            return fullResult.product.toSuggestion(
                normalizedBarcode = fullResult.product.productId?.toString() ?: barcode,
            )
        }

        var fallbackBarcode = barcode
        var fallbackSuggestedAmount: String? = null
        if (fullResult is RetailerLookupOutcome.NotFound && barcode.length > WEIGHT_SUFFIX_LENGTH) {
            val normalizedBarcode = barcode.dropLast(WEIGHT_SUFFIX_LENGTH)
            val suggestedAmount = barcode.takeLast(WEIGHT_SUFFIX_LENGTH)
                .toIntOrNull()
                ?.let { digits ->
                    BigDecimal(digits).movePointLeft(4).setScale(4, RoundingMode.HALF_UP).toPlainString()
                }
            fallbackBarcode = normalizedBarcode
            fallbackSuggestedAmount = suggestedAmount

            when (val weightedResult = lookupRetailerProduct(normalizedBarcode)) {
                is RetailerLookupOutcome.Found -> {
                    return weightedResult.product.toSuggestion(
                        normalizedBarcode = weightedResult.product.productId?.toString() ?: normalizedBarcode,
                        suggestedAmount = suggestedAmount,
                    )
                }

                is RetailerLookupOutcome.NotFound,
                RetailerLookupOutcome.Unavailable,
                -> Unit
            }
        }

        val olegonNames = lookupOlegonNames(fallbackBarcode)
        return if (olegonNames.isEmpty()) {
            null
        } else {
            ExternalLookupSuggestion(
                normalizedBarcode = fallbackBarcode,
                suggestedAmount = fallbackSuggestedAmount,
                suggestedNames = olegonNames,
            )
        }
    }

    private fun lookupSoldByWeightProduct(barcode: String): ExternalLookupSuggestion? {
        val weightedBarcode = barcode.toWeightedBarcodeSuggestion() ?: return null

        if (barcode.startsWith(WEIGHT_PRICE_LOOKUP_PREFIX)) {
            when (val weightedResult = lookupRetailerProduct(weightedBarcode.normalizedBarcode)) {
                is RetailerLookupOutcome.Found -> {
                    return weightedResult.product.toSuggestion(
                        normalizedBarcode = weightedResult.product.productId?.toString()
                            ?: weightedBarcode.normalizedBarcode,
                        suggestedAmount = weightedBarcode.suggestedAmount,
                    )
                }

                is RetailerLookupOutcome.NotFound,
                RetailerLookupOutcome.Unavailable,
                -> Unit
            }
        }

        val olegonNames = lookupOlegonNames(weightedBarcode.normalizedBarcode)
        return if (olegonNames.isEmpty()) {
            null
        } else {
            ExternalLookupSuggestion(
                normalizedBarcode = weightedBarcode.normalizedBarcode,
                suggestedAmount = weightedBarcode.suggestedAmount,
                suggestedNames = olegonNames,
            )
        }
    }

    private fun lookupRetailerProduct(barcode: String): RetailerLookupOutcome {
        val baseUrl = config.priceLookupBaseUrl ?: return RetailerLookupOutcome.Unavailable
        val sessionQuery = config.priceLookupSessionQuery ?: return RetailerLookupOutcome.Unavailable
        val requestUrl = buildString {
            append(baseUrl.removeSuffix("/"))
            append("/")
            append(URLEncoder.encode(barcode, StandardCharsets.UTF_8))
            append("?isPriceChecker=true")
            append(sessionQuery)
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(requestUrl))
            .GET()
            .build()
        val response = runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        }.getOrNull() ?: return RetailerLookupOutcome.Unavailable

        if (response.statusCode() !in 200..299) {
            return RetailerLookupOutcome.Unavailable
        }

        val body = response.body()
        val jsonObject = runCatching {
            json.decodeFromString<JsonObject>(body)
        }.getOrNull() ?: return RetailerLookupOutcome.Unavailable

        if (jsonObject.containsKey("odata.error")) {
            return RetailerLookupOutcome.NotFound
        }

        val product = runCatching {
            json.decodeFromString<RetailerProductResponse>(body)
        }.getOrNull() ?: return RetailerLookupOutcome.Unavailable

        return RetailerLookupOutcome.Found(product)
    }

    private fun lookupOlegonNames(barcode: String): List<String> {
        val baseUrl = config.barcodeLookupBaseUrl ?: return emptyList()
        val secret = config.barcodeLookupSecret ?: return emptyList()
        val requestUrl = buildString {
            append(baseUrl.removeSuffix("/"))
            append("/")
            append(URLEncoder.encode(barcode, StandardCharsets.UTF_8))
            append("/")
            append(URLEncoder.encode(secret, StandardCharsets.UTF_8))
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(requestUrl))
            .GET()
            .build()
        val response = runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        }.getOrNull() ?: return emptyList()

        if (response.statusCode() !in 200..299) {
            return emptyList()
        }

        val payload = runCatching {
            json.decodeFromString<OlegonResponse>(response.body())
        }.getOrNull() ?: return emptyList()

        return payload.names
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
    }

    private fun RetailerProductResponse.toSuggestion(
        normalizedBarcode: String,
        suggestedAmount: String? = null,
    ): ExternalLookupSuggestion {
        return ExternalLookupSuggestion(
            normalizedBarcode = normalizedBarcode,
            suggestedAmount = suggestedAmount,
            suggestedNames = listOf(name.trim()).filter(String::isNotEmpty),
            suggestedUnit = unit.toUnitCodeOrNull(),
            retailerPrice = unit.toUnitCodeOrNull()?.let { unitCode ->
                RetailerPriceEntity(
                    unit = unitCode,
                    price = formatMoney(originalPrice ?: price),
                    discountPercent = formatMoney(discount),
                    finalPrice = formatMoney(price),
                )
            },
        )
    }
}

private data class ExternalLookupSuggestion(
    val normalizedBarcode: String,
    val suggestedAmount: String? = null,
    val suggestedNames: List<String> = emptyList(),
    val suggestedUnit: UnitCode? = null,
    val retailerPrice: RetailerPriceEntity? = null,
)

private sealed interface RetailerLookupOutcome {
    data class Found(
        val product: RetailerProductResponse,
    ) : RetailerLookupOutcome

    data object NotFound : RetailerLookupOutcome

    data object Unavailable : RetailerLookupOutcome
}

private data class ExternalCatalogConfig(
    val priceLookupBaseUrl: String?,
    val priceLookupSessionQuery: String?,
    val barcodeLookupBaseUrl: String?,
    val barcodeLookupSecret: String?,
) {
    companion object {
        private val defaultSecretsRelativePath: Path =
            Paths.get("secrets", "server", "external-apis.properties")

        private fun findDefaultSecretsFile(
            userDir: String = System.getProperty("user.dir"),
        ): Path? {
            val startDirectory = Paths.get(userDir).toAbsolutePath().normalize()
            return generateSequence(startDirectory) { currentDirectory ->
                currentDirectory.parent
            }.map { currentDirectory ->
                currentDirectory.resolve(defaultSecretsRelativePath)
            }.firstOrNull(Files::isRegularFile)
        }

        fun load(
            environment: Map<String, String> = System.getenv(),
        ): ExternalCatalogConfig {
            val properties = Properties()
            findDefaultSecretsFile()?.let { secretsFile ->
                Files.newInputStream(secretsFile).use(properties::load)
            }

            fun resolve(name: String): String? {
                return properties.getProperty(name)
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: environment[name]
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
            }

            return ExternalCatalogConfig(
                priceLookupBaseUrl = resolve("PRICE_LOOKUP_BASE_URL"),
                priceLookupSessionQuery = resolve("PRICE_LOOKUP_SESSION_QUERY"),
                barcodeLookupBaseUrl = resolve("BARCODE_LOOKUP_BASE_URL")
                    ?: DEFAULT_BARCODE_LOOKUP_BASE_URL,
                barcodeLookupSecret = resolve("BARCODE_LOOKUP_SECRET"),
            )
        }
    }
}

@Serializable
private data class RetailerProductResponse(
    val productId: Long? = null,
    val name: String,
    val unit: String,
    val price: Double,
    val originalPrice: Double? = null,
    val discount: Double = 0.0,
)

@Serializable
private data class OlegonResponse(
    val status: Int,
    val names: List<String>,
)

private fun java.sql.ResultSet.toCatalogItems(): List<CatalogItemEntity> {
    val grouped = linkedMapOf<String, MutableCatalogItem>()
    while (next()) {
        val barcode = getString("barcode")
        val current = grouped.getOrPut(barcode) {
            MutableCatalogItem(
                barcode = barcode,
                mainName = getString("main_name"),
                unit = getString("unit_code").toUnitCodeOrNull() ?: UnitCode.PIECE,
            )
        }
        current.names += current.mainName
        getString("name")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(current.names::add)
    }

    return grouped.values.map { item ->
        CatalogItemEntity(
            barcode = item.barcode,
            mainName = item.mainName,
            names = item.names.toList(),
            unit = item.unit,
        )
    }
}

private data class MutableCatalogItem(
    val barcode: String,
    val mainName: String,
    val unit: UnitCode,
    val names: LinkedHashSet<String> = linkedSetOf(),
)

private fun parsePaymentTime(value: String): LocalDateTime {
    return try {
        LocalDateTime.parse(value, PAYMENT_TIME_FORMATTER)
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException("Payment time must use yyyy-MM-dd HH:mm")
    }
}

private fun parseMoney(
    value: String,
    fieldName: String,
): BigDecimal {
    val normalized = value.trim().replace(',', '.')
    val parsed = normalized.toBigDecimalOrNull()
        ?: throw IllegalArgumentException("$fieldName must be a valid number")
    require(parsed >= BigDecimal.ZERO) {
        "$fieldName must be zero or greater"
    }
    return parsed.setScale(2, RoundingMode.HALF_UP)
}

private fun parseDiscount(value: String): BigDecimal {
    val parsed = parseMoney(value, "discount_percent")
    require(parsed <= BigDecimal("100.00")) {
        "discount_percent must be 100 or less"
    }
    return parsed
}

private fun formatMoney(value: Double): String {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString()
}

private fun String.toUnitCodeOrNull(): UnitCode? {
    return runCatching {
        UnitCode.valueOf(trim().uppercase(Locale.getDefault()))
    }.getOrNull()
}

private fun looksLikeBarcode(value: String): Boolean {
    return value.isNotBlank() && value.all(Char::isDigit)
}

private fun String.asFallbackSuggestedNames(): List<String> {
    val trimmed = trim()
    return if (trimmed.isEmpty()) emptyList() else listOf(trimmed)
}

private fun String.toWeightedBarcodeSuggestion(): WeightedBarcodeSuggestion? {
    if (length <= WEIGHT_SUFFIX_LENGTH) {
        return null
    }

    val normalizedBarcode = dropLast(WEIGHT_SUFFIX_LENGTH)
    val suggestedAmount = takeLast(WEIGHT_SUFFIX_LENGTH)
        .toIntOrNull()
        ?.let { digits ->
            BigDecimal(digits).movePointLeft(4).setScale(4, RoundingMode.HALF_UP).toPlainString()
        }

    return WeightedBarcodeSuggestion(
        normalizedBarcode = normalizedBarcode,
        suggestedAmount = suggestedAmount,
    )
}

private fun normalizeForSearch(value: String): String {
    return value
        .lowercase(Locale.getDefault())
        .replace(NON_ALNUM_REGEX, " ")
        .replace(MULTIPLE_WHITESPACE_REGEX, " ")
        .trim()
}

private fun buildHighlightRanges(
    source: String,
    queryTokens: List<String>,
): List<HighlightRangeEntity> {
    if (queryTokens.isEmpty()) {
        return emptyList()
    }

    val lowercaseSource = source.lowercase(Locale.getDefault())
    return queryTokens.mapNotNull { token ->
        lowercaseSource.indexOf(token).takeIf { it >= 0 }?.let { start ->
            HighlightRangeEntity(start = start, endExclusive = start + token.length)
        }
    }.distinct()
}

private fun levenshteinDistance(
    left: String,
    right: String,
): Int {
    if (left == right) {
        return 0
    }
    if (left.isEmpty()) {
        return right.length
    }
    if (right.isEmpty()) {
        return left.length
    }

    val previous = IntArray(right.length + 1) { it }
    val current = IntArray(right.length + 1)

    left.forEachIndexed { leftIndex, leftChar ->
        current[0] = leftIndex + 1
        right.forEachIndexed { rightIndex, rightChar ->
            val substitution = previous[rightIndex] + if (leftChar == rightChar) 0 else 1
            val insertion = current[rightIndex] + 1
            val deletion = previous[rightIndex + 1] + 1
            current[rightIndex + 1] = minOf(substitution, insertion, deletion)
        }
        current.copyInto(previous)
    }

    return previous[right.length]
}

private val NON_ALNUM_REGEX = Regex("[^\\p{L}\\p{Nd}]+")
private val MULTIPLE_WHITESPACE_REGEX = Regex("\\s+")
private val PAYMENT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private const val DEFAULT_BARCODE_LOOKUP_BASE_URL = "https://barcodes.olegon.ru/api/card/name"
private const val WEIGHT_PRICE_LOOKUP_PREFIX = "25"
private const val WEIGHT_SUFFIX_LENGTH = 6

private data class WeightedBarcodeSuggestion(
    val normalizedBarcode: String,
    val suggestedAmount: String?,
)
