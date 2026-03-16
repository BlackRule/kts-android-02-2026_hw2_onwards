package com.example.myapplication.feature.itemCatalog.presentation

import androidx.lifecycle.ViewModel
import com.example.myapplication.common.ui.UiText
import com.example.myapplication.common.ui.toUiTextOr
import com.example.myapplication.feature.itemCatalog.model.CreateCatalogItemDraft
import com.example.myapplication.feature.itemCatalog.model.ItemLookupResult
import com.example.myapplication.feature.itemCatalog.model.UnitType
import com.example.myapplication.feature.itemCatalog.repository.ItemCatalogRepository
import com.example.myapplication.feature.shoppingList.presentation.PendingItemEntity
import com.example.myapplication.feature.shoppingList.presentation.ShoppingListItemsLocalStore
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
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.create_item_name_required_error
import myapplication.composeapp.generated.resources.create_item_save_error
import myapplication.composeapp.generated.resources.create_item_saved_pending_toast

data class CreateItemUiState(
    val barcodeInput: String = "",
    val mainNameInput: String = "",
    val selectedUnit: UnitType = UnitType.PIECE,
    val suggestionNames: List<String> = emptyList(),
    val lookupAmountSuggestion: String? = null,
    val lookupPriceSuggestion: String? = null,
    val lookupDiscountSuggestion: String? = null,
    val lookupFinalPriceSuggestion: String? = null,
    val isSaving: Boolean = false,
    val errorMessage: UiText? = null,
) {
    val filteredSuggestionNames: List<String>
        get() = suggestionNames
            .filter { suggestion ->
                mainNameInput.isBlank() || suggestion.contains(mainNameInput, ignoreCase = true)
            }
            .distinct()
}

sealed interface CreateItemEvent {
    data class ShowMessage(val message: UiText) : CreateItemEvent

    data object Finished : CreateItemEvent
}

class CreateItemViewModel(
    private val ownerKey: Long?,
    private val rowId: Long?,
    private val pendingItemId: Long?,
    private val initialQuery: String,
    private val soldByWeight: Boolean,
    private val localStore: ShoppingListItemsLocalStore,
    private val repository: ItemCatalogRepository = ItemCatalogRepository(),
) : ViewModel() {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(CreateItemUiState())
    val state: StateFlow<CreateItemUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CreateItemEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CreateItemEvent> = _events.asSharedFlow()

    init {
        screenScope.launch {
            loadPendingItem()
            applyInitialLookup()
        }
    }

    fun onBarcodeChanged(value: String) {
        _state.update {
            it.copy(
                barcodeInput = value,
                errorMessage = null,
            )
        }
    }

    fun onMainNameChanged(value: String) {
        _state.update {
            it.copy(
                mainNameInput = value,
                errorMessage = null,
            )
        }
    }

    fun onUnitSelected(unit: UnitType) {
        _state.update {
            it.copy(
                selectedUnit = unit,
                errorMessage = null,
            )
        }
    }

    fun saveItem() {
        val currentState = _state.value
        if (currentState.isSaving) {
            return
        }

        val normalizedName = currentState.mainNameInput.trim()
        if (normalizedName.isEmpty()) {
            _state.update {
                it.copy(errorMessage = UiText.Resource(Res.string.create_item_name_required_error))
            }
            return
        }

        screenScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }

            val normalizedBarcode = currentState.barcodeInput.trim()
            val aliasNames = buildAliasNames(
                mainName = normalizedName,
                suggestions = currentState.suggestionNames,
            )
            val existingRow = rowId?.let { localStore.getRow(it) }

            if (normalizedBarcode.isBlank()) {
                val pendingItem = if (pendingItemId != null) {
                    PendingItemEntity(
                        id = pendingItemId,
                        mainName = normalizedName,
                        aliasNames = aliasNames,
                        unit = currentState.selectedUnit,
                        barcodeDraft = "",
                    ).also { updatedItem ->
                        localStore.updatePendingItem(updatedItem)
                    }
                } else {
                    localStore.createPendingItem(
                        mainName = normalizedName,
                        aliasNames = aliasNames,
                        unit = currentState.selectedUnit,
                        barcodeDraft = "",
                    )
                }

                if (pendingItemId == null) {
                    localStore.assignPendingItemToRow(
                        ownerKey = requireNotNull(ownerKey),
                        rowId = rowId,
                        pendingItem = pendingItem,
                        price = currentState.lookupPriceSuggestion ?: existingRow?.price.orEmpty(),
                        discountPercent = currentState.lookupDiscountSuggestion ?: existingRow?.discountPercent.orEmpty(),
                        finalPrice = currentState.lookupFinalPriceSuggestion ?: existingRow?.finalPrice.orEmpty(),
                        amount = currentState.lookupAmountSuggestion ?: existingRow?.amount.orEmpty(),
                    )
                }

                _state.update { it.copy(isSaving = false) }
                _events.tryEmit(
                    CreateItemEvent.ShowMessage(UiText.Resource(Res.string.create_item_saved_pending_toast)),
                )
                _events.tryEmit(CreateItemEvent.Finished)
                return@launch
            }

            val createResult = repository.createItem(
                CreateCatalogItemDraft(
                    barcode = normalizedBarcode,
                    mainName = normalizedName,
                    aliasNames = aliasNames,
                    unit = currentState.selectedUnit,
                ),
            )

            if (createResult.isSuccess) {
                val createdItem = createResult.getOrThrow()
                if (pendingItemId != null) {
                    localStore.resolvePendingItem(
                        pendingItemId = pendingItemId,
                        item = createdItem,
                    )
                } else {
                    localStore.assignCatalogItemToRow(
                        ownerKey = requireNotNull(ownerKey),
                        rowId = rowId,
                        item = createdItem,
                        price = currentState.lookupPriceSuggestion ?: existingRow?.price.orEmpty(),
                        discountPercent = currentState.lookupDiscountSuggestion ?: existingRow?.discountPercent.orEmpty(),
                        finalPrice = currentState.lookupFinalPriceSuggestion ?: existingRow?.finalPrice.orEmpty(),
                        amount = currentState.lookupAmountSuggestion ?: existingRow?.amount.orEmpty(),
                    )
                }
                _state.update { it.copy(isSaving = false) }
                _events.tryEmit(CreateItemEvent.Finished)
            } else {
                _state.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = createResult.exceptionOrNull().toUiTextOr(
                            fallback = Res.string.create_item_save_error,
                        ),
                    )
                }
            }
        }
    }

    override fun onCleared() {
        screenScope.cancel()
        super.onCleared()
    }

    private suspend fun loadPendingItem() {
        val id = pendingItemId ?: return
        val pendingItem = localStore.getPendingItem(id) ?: return
        _state.update {
            it.copy(
                barcodeInput = pendingItem.barcodeDraft,
                mainNameInput = pendingItem.mainName,
                selectedUnit = pendingItem.unit,
                suggestionNames = pendingItem.aliasNames.ifEmpty { listOf(pendingItem.mainName) },
            )
        }
    }

    private suspend fun applyInitialLookup() {
        if (initialQuery.isBlank()) {
            return
        }

        val result = repository.lookupItem(
            query = initialQuery,
            soldByWeight = soldByWeight,
        )
        val payload = result.getOrNull()
        _state.update { current ->
            when (payload) {
                is ItemLookupResult.CreateItem -> {
                    current.copy(
                        barcodeInput = current.barcodeInput.ifBlank {
                            payload.normalizedBarcode.orEmpty()
                        },
                        mainNameInput = current.mainNameInput.ifBlank {
                            payload.suggestedNames.firstOrNull()
                                ?: initialQuery.takeUnless { it.all(Char::isDigit) }
                                .orEmpty()
                        },
                        selectedUnit = payload.suggestedUnit ?: current.selectedUnit,
                        suggestionNames = payload.suggestedNames.ifEmpty {
                            listOfNotNull(current.mainNameInput.takeIf(String::isNotBlank))
                        },
                        lookupAmountSuggestion = payload.suggestedAmount,
                        lookupPriceSuggestion = payload.retailerPricing?.price,
                        lookupDiscountSuggestion = payload.retailerPricing?.discountPercent,
                        lookupFinalPriceSuggestion = payload.retailerPricing?.finalPrice,
                    )
                }

                is ItemLookupResult.SingleMatch -> {
                    current.copy(
                        barcodeInput = current.barcodeInput.ifBlank { payload.item.barcode },
                        mainNameInput = current.mainNameInput.ifBlank { payload.item.mainName },
                        selectedUnit = payload.item.unit,
                        suggestionNames = payload.item.names,
                        lookupAmountSuggestion = payload.suggestedAmount,
                        lookupPriceSuggestion = payload.retailerPricing?.price,
                        lookupDiscountSuggestion = payload.retailerPricing?.discountPercent,
                        lookupFinalPriceSuggestion = payload.retailerPricing?.finalPrice,
                    )
                }

                is ItemLookupResult.MultipleMatches,
                null,
                -> {
                    if (current.mainNameInput.isBlank() && initialQuery.any { !it.isDigit() }) {
                        current.copy(mainNameInput = initialQuery)
                    } else {
                        current
                    }
                }
            }
        }
    }
}

private fun buildAliasNames(
    mainName: String,
    suggestions: List<String>,
): List<String> {
    return linkedSetOf(mainName).apply {
        suggestions.map(String::trim).filter(String::isNotEmpty).forEach(::add)
    }.toList()
}
