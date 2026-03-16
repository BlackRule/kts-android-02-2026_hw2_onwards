package com.example.myapplication.feature.itemCatalog.presentation

import androidx.lifecycle.ViewModel
import com.example.myapplication.feature.shoppingList.presentation.PendingItemEntity
import com.example.myapplication.feature.shoppingList.presentation.ShoppingListItemsLocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NeedBarcodeFilledUiState(
    val items: List<PendingItemEntity> = emptyList(),
)

class NeedBarcodeFilledViewModel(
    private val localStore: ShoppingListItemsLocalStore,
) : ViewModel() {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(NeedBarcodeFilledUiState())
    val state: StateFlow<NeedBarcodeFilledUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        screenScope.launch {
            _state.update {
                it.copy(items = localStore.listPendingItems())
            }
        }
    }

    fun onBarcodeDraftChanged(
        itemId: Long,
        barcodeDraft: String,
    ) {
        screenScope.launch {
            val currentItem = _state.value.items.firstOrNull { it.id == itemId } ?: return@launch
            val updatedItem = currentItem.copy(barcodeDraft = barcodeDraft)
            localStore.updatePendingItem(updatedItem)
            _state.update { state ->
                state.copy(
                    items = state.items.map { item ->
                        if (item.id == itemId) {
                            updatedItem
                        } else {
                            item
                        }
                    },
                )
            }
        }
    }

    override fun onCleared() {
        screenScope.cancel()
        super.onCleared()
    }
}
