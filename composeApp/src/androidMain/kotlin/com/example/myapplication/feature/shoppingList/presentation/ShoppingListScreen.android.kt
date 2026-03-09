package com.example.myapplication.feature.shoppingList.presentation

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.feature.shopPicker.model.ShopItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

@Composable
actual fun ShoppingListScreen(
    shoppingListId: Long?,
    selectedShopPayload: String?,
    onSelectedShopConsumed: () -> Unit,
    onBack: () -> Unit,
    onSelectShop: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier,
) {
    val viewModel: ShoppingListViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val localStore = remember(context) { ShoppingListItemsLocalStore.getInstance(context) }
    val scope = rememberCoroutineScope()
    val draftOwnerKey = rememberSaveable { -Random.nextLong(1, Long.MAX_VALUE) }
    val ownerKey = state.shoppingListId ?: draftOwnerKey
    var previousOwnerKey by rememberSaveable { mutableStateOf(ownerKey) }
    var rows by remember { mutableStateOf<List<ShoppingListEntryUiState>>(emptyList()) }

    LaunchedEffect(shoppingListId) {
        viewModel.loadShoppingList(shoppingListId)
        if (shoppingListId == null) {
            viewModel.prefillPaidAtIfEmpty(currentPaidAt())
        }
    }

    LaunchedEffect(selectedShopPayload) {
        val payload = selectedShopPayload ?: return@LaunchedEffect
        runCatching {
            navigationJson.decodeFromString<ShopItem>(payload)
        }.getOrNull()?.let(viewModel::onSelectedShop)
        onSelectedShopConsumed()
    }

    LaunchedEffect(state.saveSucceeded) {
        if (state.saveSucceeded) {
            if (previousOwnerKey != ownerKey) {
                localStore.migrateOwner(previousOwnerKey, ownerKey)
                previousOwnerKey = ownerKey
            }
            viewModel.consumeSaveSuccess()
            onSaved()
        }
    }

    LaunchedEffect(ownerKey) {
        if (previousOwnerKey != ownerKey) {
            localStore.migrateOwner(previousOwnerKey, ownerKey)
            previousOwnerKey = ownerKey
        }
        rows = localStore.listItems(ownerKey).map(ShoppingListItemEntity::toUiState)
    }

    ShoppingListContent(
        state = state,
        onBack = onBack,
        onSelectShop = onSelectShop,
        onClearShop = viewModel::clearSelectedShop,
        onPaidAtChanged = viewModel::onPaidAtChanged,
        onPickPaidAt = {
            showPaidAtPicker(
                context = context,
                initialPaidAt = state.paidAtInput,
                onPaidAtSelected = viewModel::onPaidAtChanged,
            )
        },
        onUseCurrentTime = {
            viewModel.onPaidAtChanged(currentPaidAt())
        },
        onTotalChanged = viewModel::onTotalChanged,
        shoppingRows = rows,
        onAddRow = {
            scope.launch {
                val created = localStore.addEmptyRow(ownerKey).toUiState()
                rows = rows + created
            }
        },
        onRowItemChanged = { index, value ->
            rows = updateRowAt(
                rows = rows,
                index = index,
                ownerKey = ownerKey,
                localStore = localStore,
                scope = scope,
            ) { row ->
                row.copy(item = value)
            }
        },
        onRowPriceChanged = { index, value ->
            rows = updateRowAt(
                rows = rows,
                index = index,
                ownerKey = ownerKey,
                localStore = localStore,
                scope = scope,
            ) { row ->
                row.copy(priceInput = value).withCalculatedThirdField()
            }
        },
        onRowDiscountChanged = { index, value ->
            rows = updateRowAt(
                rows = rows,
                index = index,
                ownerKey = ownerKey,
                localStore = localStore,
                scope = scope,
            ) { row ->
                row.copy(discountPercentInput = value).withCalculatedThirdField()
            }
        },
        onRowFinalPriceChanged = { index, value ->
            rows = updateRowAt(
                rows = rows,
                index = index,
                ownerKey = ownerKey,
                localStore = localStore,
                scope = scope,
            ) { row ->
                row.copy(finalPriceInput = value).withCalculatedThirdField()
            }
        },
        onRowAmountChanged = { index, value ->
            rows = updateRowAt(
                rows = rows,
                index = index,
                ownerKey = ownerKey,
                localStore = localStore,
                scope = scope,
            ) { row ->
                row.copy(amountInput = value)
            }
        },
        onSave = viewModel::saveShoppingList,
        modifier = modifier,
    )
}

private fun updateRowAt(
    rows: List<ShoppingListEntryUiState>,
    index: Int,
    ownerKey: Long,
    localStore: ShoppingListItemsLocalStore,
    scope: CoroutineScope,
    transform: (ShoppingListEntryUiState) -> ShoppingListEntryUiState,
): List<ShoppingListEntryUiState> {
    if (index !in rows.indices) {
        return rows
    }

    val updatedRows = rows.toMutableList()
    val updatedRow = transform(updatedRows[index])
    updatedRows[index] = updatedRow

    val rowId = updatedRow.localId
    if (rowId != null) {
        scope.launch {
            localStore.updateItem(
                ShoppingListItemEntity(
                    id = rowId,
                    ownerKey = ownerKey,
                    item = updatedRow.item,
                    price = updatedRow.priceInput,
                    discountPercent = updatedRow.discountPercentInput,
                    finalPrice = updatedRow.finalPriceInput,
                    amount = updatedRow.amountInput,
                ),
            )
        }
    }

    return updatedRows
}

private fun showPaidAtPicker(
    context: android.content.Context,
    initialPaidAt: String,
    onPaidAtSelected: (String) -> Unit,
) {
    val initialDateTime = parsePaidAt(initialPaidAt) ?: LocalDateTime.now()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    onPaidAtSelected(
                        LocalDateTime.of(year, month + 1, dayOfMonth, hourOfDay, minute)
                            .format(paidAtFormatter),
                    )
                },
                initialDateTime.hour,
                initialDateTime.minute,
                true,
            ).show()
        },
        initialDateTime.year,
        initialDateTime.monthValue - 1,
        initialDateTime.dayOfMonth,
    ).show()
}

private fun currentPaidAt(): String {
    return LocalDateTime.now().format(paidAtFormatter)
}

private fun parsePaidAt(value: String): LocalDateTime? {
    return runCatching {
        LocalDateTime.parse(value.trim(), paidAtFormatter)
    }.getOrNull()
}

private val paidAtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private val navigationJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private fun ShoppingListItemEntity.toUiState(): ShoppingListEntryUiState {
    return ShoppingListEntryUiState(
        localId = id,
        item = item,
        priceInput = price,
        discountPercentInput = discountPercent,
        finalPriceInput = finalPrice,
        amountInput = amount,
    )
}

private fun ShoppingListEntryUiState.withCalculatedThirdField(): ShoppingListEntryUiState {
    val priceValue = priceInput.toDoubleOrNull()
    val discountValue = discountPercentInput.toDoubleOrNull()
    val finalPriceValue = finalPriceInput.toDoubleOrNull()

    val filledFields = listOf(priceValue, discountValue, finalPriceValue).count { it != null }
    if (filledFields != 2) {
        return this
    }

    return when {
        priceValue == null && discountValue != null && finalPriceValue != null -> {
            val divisor = 1.0 - (discountValue / 100.0)
            if (divisor <= 0.0) {
                this
            } else {
                copy(priceInput = formatDecimal(finalPriceValue / divisor))
            }
        }

        discountValue == null && priceValue != null && finalPriceValue != null -> {
            if (priceValue == 0.0) {
                this
            } else {
                copy(
                    discountPercentInput = formatDecimal(
                        (1.0 - (finalPriceValue / priceValue)) * 100.0,
                    ),
                )
            }
        }

        finalPriceValue == null && priceValue != null && discountValue != null -> {
            copy(finalPriceInput = formatDecimal(priceValue * (1.0 - discountValue / 100.0)))
        }

        else -> this
    }
}

private fun formatDecimal(value: Double): String {
    if (!value.isFinite()) {
        return ""
    }
    val normalizedValue = if (value < 0.0) 0.0 else value
    val formatted = String.format(Locale.US, "%.2f", normalizedValue)
    return formatted.trimEnd('0').trimEnd('.')
}
