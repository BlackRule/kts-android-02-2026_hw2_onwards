package com.example.myapplication.feature.itemCatalog.presentation

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import com.example.myapplication.common.ui.UiText
import com.example.myapplication.common.ui.toUiTextOr
import com.example.myapplication.feature.itemCatalog.model.HighlightRange
import com.example.myapplication.feature.itemCatalog.model.ItemLookupMatch
import com.example.myapplication.feature.itemCatalog.model.ItemLookupResult
import com.example.myapplication.feature.itemCatalog.repository.ItemCatalogRepository
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
import myapplication.composeapp.generated.resources.item_select_empty_query_error
import myapplication.composeapp.generated.resources.item_select_lookup_error

data class ItemSelectUiState(
    val query: String = "",
    val matches: List<ItemLookupMatch> = emptyList(),
    val selectedIndex: Int = 0,
    val soldByWeight: Boolean = false,
    val isSearching: Boolean = false,
    val errorMessage: UiText? = null,
)

sealed interface ItemSelectEvent {
    data class OpenCreateItem(
        val initialQuery: String,
        val soldByWeight: Boolean,
    ) : ItemSelectEvent

    data object NavigateBack : ItemSelectEvent
}

class ItemSelectViewModel(
    private val ownerKey: Long?,
    private val rowId: Long?,
    private val pendingItemId: Long?,
    private val initialQuery: String,
    private val localStore: ShoppingListItemsLocalStore,
    private val repository: ItemCatalogRepository = ItemCatalogRepository(),
) : ViewModel() {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastScannedCode: String = ""
    private var lastScannedAt: Long = 0L

    private val _state = MutableStateFlow(ItemSelectUiState(query = initialQuery))
    val state: StateFlow<ItemSelectUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ItemSelectEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ItemSelectEvent> = _events.asSharedFlow()

    init {
        if (initialQuery.isNotBlank()) {
            onSearchRequested()
        }
    }

    fun onQueryChanged(value: String) {
        _state.update {
            it.copy(
                query = value,
                errorMessage = null,
            )
        }
    }

    fun onSoldByWeightChanged(value: Boolean) {
        _state.update { it.copy(soldByWeight = value, errorMessage = null) }
    }

    fun onSearchRequested() {
        val currentState = _state.value
        if (currentState.isSearching) {
            return
        }

        screenScope.launch {
            performLookup(currentState.query)
        }
    }

    fun onBarcodeScanned(scannedCode: String) {
        val now = platformElapsedRealtimeMillis()
        if (scannedCode == lastScannedCode && now - lastScannedAt < 1_500L) {
            return
        }

        lastScannedCode = scannedCode
        lastScannedAt = now
        if (_state.value.isSearching) {
            return
        }

        screenScope.launch {
            performLookup(scannedCode)
        }
    }

    fun onMatchSelected(index: Int) {
        _state.update { it.copy(selectedIndex = index) }
    }

    fun onUseSelectedMatch() {
        val selectedMatch = _state.value.matches.getOrNull(_state.value.selectedIndex) ?: return
        screenScope.launch {
            applyLookupSelection(
                localStore = localStore,
                ownerKey = ownerKey,
                rowId = rowId,
                pendingItemId = pendingItemId,
                lookup = ItemLookupResult.SingleMatch(item = selectedMatch.item),
            )
            _events.tryEmit(ItemSelectEvent.NavigateBack)
        }
    }

    override fun onCleared() {
        screenScope.cancel()
        super.onCleared()
    }

    private suspend fun performLookup(lookupQuery: String) {
        val normalizedQuery = lookupQuery.trim()
        if (normalizedQuery.isEmpty()) {
            _state.update {
                it.copy(
                    matches = emptyList(),
                    errorMessage = UiText.Resource(Res.string.item_select_empty_query_error),
                )
            }
            return
        }

        _state.update {
            it.copy(
                query = normalizedQuery,
                isSearching = true,
                matches = emptyList(),
                selectedIndex = 0,
                errorMessage = null,
            )
        }

        val result = repository.lookupItem(
            query = normalizedQuery,
            soldByWeight = _state.value.soldByWeight,
        )

        if (result.isFailure) {
            _state.update {
                it.copy(
                    isSearching = false,
                    matches = emptyList(),
                    errorMessage = result.exceptionOrNull().toUiTextOr(
                        fallback = Res.string.item_select_lookup_error,
                    ),
                )
            }
            return
        }

        when (val lookup = result.getOrThrow()) {
            is ItemLookupResult.SingleMatch -> {
                applyLookupSelection(
                    localStore = localStore,
                    ownerKey = ownerKey,
                    rowId = rowId,
                    pendingItemId = pendingItemId,
                    lookup = lookup,
                )
                _state.update { it.copy(isSearching = false, errorMessage = null) }
                _events.tryEmit(ItemSelectEvent.NavigateBack)
            }

            is ItemLookupResult.MultipleMatches -> {
                _state.update {
                    it.copy(
                        matches = lookup.matches,
                        selectedIndex = 0,
                        isSearching = false,
                        errorMessage = null,
                    )
                }
            }

            is ItemLookupResult.CreateItem -> {
                _state.update { it.copy(isSearching = false, errorMessage = null) }
                _events.tryEmit(
                    ItemSelectEvent.OpenCreateItem(
                        initialQuery = normalizedQuery,
                        soldByWeight = _state.value.soldByWeight,
                    ),
                )
            }
        }
    }
}

suspend fun applyLookupSelection(
    localStore: ShoppingListItemsLocalStore,
    ownerKey: Long?,
    rowId: Long?,
    pendingItemId: Long?,
    lookup: ItemLookupResult.SingleMatch,
) {
    if (pendingItemId != null) {
        localStore.resolvePendingItem(
            pendingItemId = pendingItemId,
            item = lookup.item,
        )
        return
    }

    val effectiveOwnerKey = requireNotNull(ownerKey) {
        "ownerKey is required when assigning a row item"
    }
    val existingRow = rowId?.let { localStore.getRow(it) }

    localStore.assignCatalogItemToRow(
        ownerKey = effectiveOwnerKey,
        rowId = rowId,
        item = lookup.item,
        price = lookup.retailerPricing?.price ?: existingRow?.price.orEmpty(),
        discountPercent = lookup.retailerPricing?.discountPercent ?: existingRow?.discountPercent.orEmpty(),
        finalPrice = lookup.retailerPricing?.finalPrice ?: existingRow?.finalPrice.orEmpty(),
        amount = lookup.suggestedAmount ?: existingRow?.amount.orEmpty(),
    )
}

fun highlightedMatchText(
    source: String,
    ranges: List<HighlightRange>,
) = buildAnnotatedString {
    if (ranges.isEmpty()) {
        append(source)
        return@buildAnnotatedString
    }

    var cursor = 0
    ranges.sortedBy(HighlightRange::start).forEach { range ->
        val safeStart = range.start.coerceIn(0, source.length)
        val safeEnd = range.endExclusive.coerceIn(safeStart, source.length)
        if (cursor < safeStart) {
            append(source.substring(cursor, safeStart))
        }
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        append(source.substring(safeStart, safeEnd))
        pop()
        cursor = safeEnd
    }
    if (cursor < source.length) {
        append(source.substring(cursor))
    }
}
