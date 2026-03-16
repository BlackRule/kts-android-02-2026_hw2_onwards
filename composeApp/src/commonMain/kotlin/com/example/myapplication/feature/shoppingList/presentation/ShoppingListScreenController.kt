package com.example.myapplication.feature.shoppingList.presentation

import androidx.compose.runtime.Composable
import com.example.myapplication.common.ui.UiText
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class ShoppingListScreenUiState(
    val formState: ShoppingListUiState = ShoppingListUiState(),
    val rows: List<ShoppingListEntryUiState> = emptyList(),
    val ownerKey: Long? = null,
    val showPaidConfirmation: Boolean = false,
    val paidConfirmationMessage: UiText? = null,
    val isPaying: Boolean = false,
) {
    val effectiveFormState: ShoppingListUiState
        get() = formState.copy(isSaving = formState.isSaving || isPaying)

    val unresolvedCount: Int
        get() = rows.count { it.itemBarcode.isNullOrBlank() }
}

sealed interface ShoppingListScreenEvent {
    data class ShowMessage(val message: UiText) : ShoppingListScreenEvent

    data class OpenItemSelect(val ownerKey: Long, val rowId: Long?) : ShoppingListScreenEvent

    data object OpenNeedBarcode : ShoppingListScreenEvent

    data object Saved : ShoppingListScreenEvent
}

interface ShoppingListScreenController {
    val state: StateFlow<ShoppingListScreenUiState>
    val events: SharedFlow<ShoppingListScreenEvent>

    fun onSelectedShopPayload(payload: String?)

    fun clearSelectedShop()

    fun onPaidAtChanged(paidAt: String)

    fun useCurrentTime()

    fun onRowPriceChanged(index: Int, value: String)

    fun onRowDiscountChanged(index: Int, value: String)

    fun onRowFinalPriceChanged(index: Int, value: String)

    fun onRowAmountChanged(index: Int, value: String)

    fun onAddRowRequested()

    fun onRowItemRequested(index: Int)

    fun saveAndExit()

    fun requestMarkAsPaid()

    fun dismissPaidConfirmation()

    fun confirmMarkAsPaid()
}

@Composable
expect fun rememberShoppingListScreenController(shoppingListId: Long?): ShoppingListScreenController

@Composable
expect fun rememberPaidAtPickerLauncher(onPaidAtSelected: (String) -> Unit): (String) -> Unit
