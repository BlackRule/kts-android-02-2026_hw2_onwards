package com.example.myapplication.feature.shoppingList.presentation

import androidx.lifecycle.ViewModel
import com.example.myapplication.common.ui.UiText
import com.example.myapplication.common.ui.toUiTextOr
import com.example.myapplication.feature.itemCatalog.model.PriceObservationImportRow
import com.example.myapplication.feature.itemCatalog.model.UnitType
import com.example.myapplication.feature.itemCatalog.repository.ItemCatalogRepository
import com.example.myapplication.feature.shopPicker.model.ShopItem
import com.example.myapplication.feature.shoppingLists.model.toMoneyAmountString
import com.example.myapplication.feature.shoppingLists.repository.ShoppingListsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.shopping_list_load_error
import myapplication.composeapp.generated.resources.shopping_list_paid_at_error
import myapplication.composeapp.generated.resources.shopping_list_paid_confirm_message
import myapplication.composeapp.generated.resources.shopping_list_paid_kg_amount_error
import myapplication.composeapp.generated.resources.shopping_list_paid_missing_prices_error
import myapplication.composeapp.generated.resources.shopping_list_paid_missing_unit_error
import myapplication.composeapp.generated.resources.shopping_list_paid_piece_amount_error
import myapplication.composeapp.generated.resources.shopping_list_paid_upload_error
import myapplication.composeapp.generated.resources.shopping_list_paid_upload_success_toast
import myapplication.composeapp.generated.resources.shopping_list_save_error
import myapplication.composeapp.generated.resources.shopping_list_saved_without_upload_toast
import myapplication.composeapp.generated.resources.shopping_list_select_shop_error
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class ShoppingListScreenViewModel(
    private val shoppingListId: Long?,
    private val localStore: ShoppingListItemsLocalStore,
    private val shoppingListsRepository: ShoppingListsRepository = ShoppingListsRepository(),
    private val itemCatalogRepository: ItemCatalogRepository = ItemCatalogRepository(),
) : ViewModel(), ShoppingListScreenController {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val draftOwnerKey = -Random.nextLong(1, Long.MAX_VALUE)

    private val _state = MutableStateFlow(
        ShoppingListScreenUiState(
            ownerKey = shoppingListId ?: draftOwnerKey,
            formState = ShoppingListUiState(
                shoppingListId = shoppingListId,
                paidAtInput = if (shoppingListId == null) currentPaidAt() else "",
            ),
        ),
    )
    override val state: StateFlow<ShoppingListScreenUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ShoppingListScreenEvent>(extraBufferCapacity = 4)
    override val events: SharedFlow<ShoppingListScreenEvent> = _events.asSharedFlow()

    init {
        screenScope.launch {
            if (shoppingListId == null) {
                refreshRows()
            } else {
                loadShoppingList(shoppingListId)
            }
        }
    }

    override fun onSelectedShopPayload(payload: String?) {
        val selectedShop = payload?.let {
            runCatching { navigationJson.decodeFromString<ShopItem>(it) }.getOrNull()
        } ?: return
        updateFormState {
            copy(
                selectedShop = selectedShop,
                errorMessage = null,
            )
        }
    }

    fun refreshRows() {
        screenScope.launch {
            val ownerKey = _state.value.ownerKey ?: return@launch
            val rows = localStore.listRows(ownerKey).map(ShoppingListRowEntity::toUiState)
            _state.update { current ->
                current.copy(
                    rows = rows,
                    formState = current.formState.copy(
                        totalDisplay = rows.calculateOverallTotalDisplay(),
                        errorMessage = null,
                    ),
                )
            }
        }
    }

    override fun clearSelectedShop() {
        updateFormState {
            copy(
                selectedShop = null,
                errorMessage = null,
            )
        }
    }

    override fun onPaidAtChanged(paidAt: String) {
        updateFormState {
            copy(
                paidAtInput = paidAt,
                errorMessage = null,
            )
        }
    }

    override fun useCurrentTime() {
        updateFormState {
            copy(
                paidAtInput = currentPaidAt(),
                errorMessage = null,
            )
        }
    }

    override fun onRowPriceChanged(index: Int, value: String) {
        updateRow(index) { row -> row.copy(priceInput = value).withCalculatedThirdField() }
    }

    override fun onRowDiscountChanged(index: Int, value: String) {
        updateRow(index) { row -> row.copy(discountPercentInput = value).withCalculatedThirdField() }
    }

    override fun onRowFinalPriceChanged(index: Int, value: String) {
        updateRow(index) { row -> row.copy(finalPriceInput = value).withCalculatedThirdField() }
    }

    override fun onRowAmountChanged(index: Int, value: String) {
        updateRow(index) { row -> row.copy(amountInput = value).withRecomputedTotal() }
    }

    override fun onAddRowRequested() {
        val ownerKey = _state.value.ownerKey ?: return
        _events.tryEmit(ShoppingListScreenEvent.OpenItemSelect(ownerKey = ownerKey, rowId = null))
    }

    override fun onRowItemRequested(index: Int) {
        val currentState = _state.value
        val ownerKey = currentState.ownerKey ?: return
        val row = currentState.rows.getOrNull(index) ?: return
        if (row.pendingItemId != null) {
            _events.tryEmit(ShoppingListScreenEvent.OpenNeedBarcode)
        } else {
            _events.tryEmit(
                ShoppingListScreenEvent.OpenItemSelect(
                    ownerKey = ownerKey,
                    rowId = row.localId,
                ),
            )
        }
    }

    override fun saveAndExit() {
        screenScope.launch {
            persistShoppingList(
                navigateOnSuccess = true,
                importAfterSave = false,
            )
        }
    }

    override fun requestMarkAsPaid() {
        val currentState = _state.value
        val validationMessage = validateBeforePaidImport(currentState)
        if (validationMessage != null) {
            updateFormState { copy(errorMessage = validationMessage) }
            return
        }

        val validation = validateRowsForPaidImport(currentState.rows)
        _state.update {
            it.copy(
                showPaidConfirmation = true,
                paidConfirmationMessage = UiText.Resource(
                    Res.string.shopping_list_paid_confirm_message,
                    listOf(validation.importRows.size, validation.unresolvedCount),
                ),
            )
        }
    }

    override fun dismissPaidConfirmation() {
        _state.update {
            it.copy(
                showPaidConfirmation = false,
                paidConfirmationMessage = null,
            )
        }
    }

    override fun confirmMarkAsPaid() {
        dismissPaidConfirmation()
        screenScope.launch {
            persistShoppingList(
                navigateOnSuccess = false,
                importAfterSave = true,
            )
        }
    }

    override fun onCleared() {
        screenScope.cancel()
        super.onCleared()
    }

    private suspend fun loadShoppingList(id: Long) {
        updateFormState {
            copy(
                shoppingListId = id,
                isLoading = true,
                errorMessage = null,
            )
        }

        val result = shoppingListsRepository.getShoppingList(id)
        if (result.isSuccess) {
            val shoppingList = result.getOrThrow()
            _state.update {
                it.copy(
                    ownerKey = shoppingList.id,
                    formState = it.formState.copy(
                        shoppingListId = shoppingList.id,
                        selectedShop = shoppingList.shop,
                        paidAtInput = shoppingList.paidAt,
                        totalDisplay = shoppingList.totalAmountMinor.toMoneyAmountString(),
                        isLoading = false,
                        errorMessage = null,
                    ),
                )
            }
            refreshRows()
        } else {
            updateFormState {
                copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull().toUiTextOr(
                        fallback = Res.string.shopping_list_load_error,
                    ),
                )
            }
        }
    }

    private suspend fun persistShoppingList(
        navigateOnSuccess: Boolean,
        importAfterSave: Boolean,
    ) {
        val currentState = _state.value
        if (currentState.formState.isLoading || currentState.formState.isSaving || currentState.isPaying) {
            return
        }

        val validationMessage = if (importAfterSave) {
            validateBeforePaidImport(currentState)
        } else {
            validateBeforeSave(currentState.formState)
        }
        if (validationMessage != null) {
            updateFormState { copy(errorMessage = validationMessage) }
            return
        }

        val selectedShop = requireNotNull(currentState.formState.selectedShop)
        val paidAt = currentState.formState.paidAtInput.trim()
        updateFormState {
            copy(
                isSaving = true,
                errorMessage = null,
            )
        }

        val totalAmountMinor = currentState.rows.calculateOverallTotalMinorUnits()
        val result = if (currentState.formState.shoppingListId == null) {
            shoppingListsRepository.createShoppingList(
                shopId = selectedShop.id,
                paidAt = paidAt,
                totalAmountMinor = totalAmountMinor,
            )
        } else {
            shoppingListsRepository.updateShoppingList(
                id = currentState.formState.shoppingListId,
                shopId = selectedShop.id,
                paidAt = paidAt,
                totalAmountMinor = totalAmountMinor,
            )
        }

        if (result.isFailure) {
            updateFormState {
                copy(
                    isSaving = false,
                    errorMessage = result.exceptionOrNull().toUiTextOr(
                        fallback = Res.string.shopping_list_save_error,
                    ),
                )
            }
            return
        }

        val savedShoppingList = result.getOrThrow()
        val previousOwnerKey = currentState.ownerKey
        if (previousOwnerKey != null && previousOwnerKey != savedShoppingList.id) {
            localStore.migrateOwner(previousOwnerKey, savedShoppingList.id)
        }

        val savedRows = localStore.listRows(savedShoppingList.id).map(ShoppingListRowEntity::toUiState)
        _state.update {
            it.copy(
                rows = savedRows,
                ownerKey = savedShoppingList.id,
                formState = it.formState.copy(
                    shoppingListId = savedShoppingList.id,
                    selectedShop = savedShoppingList.shop,
                    paidAtInput = savedShoppingList.paidAt,
                    totalDisplay = savedRows.calculateOverallTotalDisplay(),
                    isSaving = false,
                    errorMessage = null,
                ),
            )
        }

        if (importAfterSave) {
            importPaidRows(
                shopId = savedShoppingList.shop.id,
                paymentTime = savedShoppingList.paidAt,
                rows = savedRows,
            )
        } else if (navigateOnSuccess) {
            _events.tryEmit(ShoppingListScreenEvent.Saved)
        }
    }

    private suspend fun importPaidRows(
        shopId: Long,
        paymentTime: String,
        rows: List<ShoppingListEntryUiState>,
    ) {
        val validation = validateRowsForPaidImport(rows)
        if (validation.errorMessage != null) {
            updateFormState { copy(errorMessage = validation.errorMessage) }
            return
        }

        if (validation.importRows.isEmpty()) {
            _events.tryEmit(
                ShoppingListScreenEvent.ShowMessage(
                    UiText.Resource(Res.string.shopping_list_saved_without_upload_toast),
                ),
            )
            return
        }

        _state.update { it.copy(isPaying = true) }
        val result = itemCatalogRepository.importPriceObservations(
            shopId = shopId,
            paymentTime = paymentTime,
            rows = validation.importRows,
        )
        _state.update { it.copy(isPaying = false) }

        if (result.isSuccess) {
            val payload = result.getOrThrow()
            updateFormState { copy(errorMessage = null) }
            _events.tryEmit(
                ShoppingListScreenEvent.ShowMessage(
                    UiText.Resource(
                        Res.string.shopping_list_paid_upload_success_toast,
                        listOf(payload.insertedCount, payload.skippedCount, validation.unresolvedCount),
                    ),
                ),
            )
        } else {
            updateFormState {
                copy(
                    errorMessage = result.exceptionOrNull().toUiTextOr(
                        fallback = Res.string.shopping_list_paid_upload_error,
                    ),
                )
            }
        }
    }

    private fun validateBeforeSave(formState: ShoppingListUiState): UiText? {
        if (formState.selectedShop == null) {
            return UiText.Resource(Res.string.shopping_list_select_shop_error)
        }
        if (!paidAtPattern.matches(formState.paidAtInput.trim())) {
            return UiText.Resource(Res.string.shopping_list_paid_at_error)
        }
        return null
    }

    private fun validateBeforePaidImport(
        screenState: ShoppingListScreenUiState,
    ): UiText? {
        return validateBeforeSave(screenState.formState)
            ?: validateRowsForPaidImport(screenState.rows).errorMessage
    }

    private fun updateFormState(
        transform: ShoppingListUiState.() -> ShoppingListUiState,
    ) {
        _state.update { current ->
            current.copy(formState = current.formState.transform())
        }
    }

    private fun updateRow(
        index: Int,
        transform: (ShoppingListEntryUiState) -> ShoppingListEntryUiState,
    ) {
        val currentState = _state.value
        if (index !in currentState.rows.indices) {
            return
        }

        val ownerKey = currentState.ownerKey ?: return
        val updatedRows = currentState.rows.toMutableList()
        val updatedRow = transform(updatedRows[index]).withRecomputedTotal()
        updatedRows[index] = updatedRow

        _state.update {
            it.copy(
                rows = updatedRows,
                formState = it.formState.copy(
                    totalDisplay = updatedRows.calculateOverallTotalDisplay(),
                    errorMessage = null,
                ),
            )
        }

        val rowId = updatedRow.localId ?: return
        screenScope.launch {
            localStore.updateRow(updatedRow.toEntity(ownerKey))
        }
    }
}

private data class PaidImportValidation(
    val importRows: List<PriceObservationImportRow>,
    val unresolvedCount: Int,
    val errorMessage: UiText? = null,
)

private fun validateRowsForPaidImport(
    rows: List<ShoppingListEntryUiState>,
): PaidImportValidation {
    val unresolvedRows = rows.filter { it.itemBarcode.isNullOrBlank() }
    val resolvedRows = rows.filterNot { it.itemBarcode.isNullOrBlank() }

    resolvedRows.forEach { row ->
        val price = parseMoneyDecimal(row.priceInput)
        val discount = parseMoneyDecimal(row.discountPercentInput)
        val finalPrice = parseMoneyDecimal(row.finalPriceInput)
        val amount = parseAmount(row.amountInput, row.unit)

        if (price == null || discount == null || finalPrice == null) {
            return PaidImportValidation(
                importRows = emptyList(),
                unresolvedCount = unresolvedRows.size,
                errorMessage = UiText.Resource(Res.string.shopping_list_paid_missing_prices_error),
            )
        }

        if (amount == null || amount <= BigDecimal.ZERO) {
            return PaidImportValidation(
                importRows = emptyList(),
                unresolvedCount = unresolvedRows.size,
                errorMessage = when (row.unit) {
                    UnitType.PIECE -> UiText.Resource(Res.string.shopping_list_paid_piece_amount_error)
                    UnitType.KG -> UiText.Resource(Res.string.shopping_list_paid_kg_amount_error)
                    null -> UiText.Resource(Res.string.shopping_list_paid_missing_unit_error)
                },
            )
        }
    }

    return PaidImportValidation(
        importRows = resolvedRows.map { row ->
            PriceObservationImportRow(
                itemBarcode = requireNotNull(row.itemBarcode),
                price = normalizeMoney(row.priceInput),
                discountPercent = normalizeMoney(row.discountPercentInput),
                finalPrice = normalizeMoney(row.finalPriceInput),
            )
        },
        unresolvedCount = unresolvedRows.size,
    )
}

private fun ShoppingListRowEntity.toUiState(): ShoppingListEntryUiState {
    return ShoppingListEntryUiState(
        localId = id,
        itemBarcode = itemBarcode,
        itemMainName = itemMainName,
        pendingItemId = pendingItemId,
        unit = unit,
        priceInput = price,
        discountPercentInput = discountPercent,
        finalPriceInput = finalPrice,
        amountInput = amount,
    ).withRecomputedTotal()
}

private fun ShoppingListEntryUiState.toEntity(ownerKey: Long): ShoppingListRowEntity {
    return ShoppingListRowEntity(
        id = requireNotNull(localId),
        ownerKey = ownerKey,
        itemBarcode = itemBarcode,
        itemMainName = itemMainName,
        pendingItemId = pendingItemId,
        unit = unit,
        price = priceInput,
        discountPercent = discountPercentInput,
        finalPrice = finalPriceInput,
        amount = amountInput,
    )
}

private fun ShoppingListEntryUiState.withCalculatedThirdField(): ShoppingListEntryUiState {
    val priceValue = parseMoneyDecimal(priceInput)
    val discountValue = parseMoneyDecimal(discountPercentInput)
    val finalPriceValue = parseMoneyDecimal(finalPriceInput)

    val filledFields = listOf(priceValue, discountValue, finalPriceValue).count { it != null }
    if (filledFields != 2) {
        return withRecomputedTotal()
    }

    return when {
        priceValue == null && discountValue != null && finalPriceValue != null -> {
            val divisor = BigDecimal.ONE.subtract(discountValue.divide(BigDecimal(100)))
            if (divisor <= BigDecimal.ZERO) {
                this
            } else {
                copy(priceInput = formatMoney(finalPriceValue.divide(divisor, 2, RoundingMode.HALF_UP)))
            }
        }

        discountValue == null && priceValue != null && finalPriceValue != null -> {
            if (priceValue.compareTo(BigDecimal.ZERO) == 0) {
                this
            } else {
                val ratio = finalPriceValue.divide(priceValue, 4, RoundingMode.HALF_UP)
                copy(
                    discountPercentInput = formatMoney(
                        BigDecimal(100).multiply(BigDecimal.ONE.subtract(ratio)),
                    ),
                )
            }
        }

        finalPriceValue == null && priceValue != null && discountValue != null -> {
            val multiplier = BigDecimal.ONE.subtract(discountValue.divide(BigDecimal(100), 4, RoundingMode.HALF_UP))
            copy(finalPriceInput = formatMoney(priceValue.multiply(multiplier)))
        }

        else -> this
    }.withRecomputedTotal()
}

private fun ShoppingListEntryUiState.withRecomputedTotal(): ShoppingListEntryUiState {
    val finalPriceValue = parseMoneyDecimal(finalPriceInput)
    val amountValue = parseFlexibleDecimal(amountInput)
    val total = if (finalPriceValue != null && amountValue != null) {
        formatMoney(finalPriceValue.multiply(amountValue))
    } else {
        ""
    }
    return copy(totalDisplay = total)
}

private fun List<ShoppingListEntryUiState>.calculateOverallTotalDisplay(): String {
    return formatMoney(
        fold(BigDecimal.ZERO) { acc, row ->
            acc + (parseMoneyDecimal(row.totalDisplay) ?: BigDecimal.ZERO)
        },
    )
}

private fun List<ShoppingListEntryUiState>.calculateOverallTotalMinorUnits(): Long {
    val total = fold(BigDecimal.ZERO) { acc, row ->
        acc + (parseMoneyDecimal(row.totalDisplay) ?: BigDecimal.ZERO)
    }.setScale(2, RoundingMode.HALF_UP)
    return total.movePointRight(2).longValueExact()
}

private fun parseAmount(
    value: String,
    unit: UnitType?,
): BigDecimal? {
    val parsed = parseFlexibleDecimal(value) ?: return null
    return when (unit) {
        UnitType.PIECE -> {
            if (parsed.scale() <= 0 || parsed.stripTrailingZeros().scale() <= 0) {
                parsed
            } else {
                null
            }
        }

        UnitType.KG -> parsed
        null -> null
    }
}

private fun parseMoneyDecimal(value: String): BigDecimal? {
    val normalized = value.trim().replace(',', '.')
    if (normalized.isBlank()) {
        return null
    }
    return normalized.toBigDecimalOrNull()
        ?.setScale(2, RoundingMode.HALF_UP)
}

private fun normalizeMoney(value: String): String {
    return formatMoney(parseMoneyDecimal(value) ?: BigDecimal.ZERO)
}

private fun formatMoney(value: BigDecimal): String {
    return value.setScale(2, RoundingMode.HALF_UP).toPlainString()
}

private fun parseFlexibleDecimal(value: String): BigDecimal? {
    val normalized = value.trim().replace(',', '.')
    if (normalized.isBlank()) {
        return null
    }
    return normalized.toBigDecimalOrNull()
}

private fun currentPaidAt(): String {
    return LocalDateTime.now().format(paidAtFormatter)
}

private val paidAtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val paidAtPattern = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")

private val navigationJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
