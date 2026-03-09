package com.example.myapplication.feature.shoppingList.presentation

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.feature.shopPicker.model.ShopItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
            viewModel.consumeSaveSuccess()
            onSaved()
        }
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
        onSave = viewModel::saveShoppingList,
        modifier = modifier,
    )
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
