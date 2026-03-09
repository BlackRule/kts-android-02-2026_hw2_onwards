package com.example.myapplication.feature.shoppingList.presentation

import androidx.lifecycle.ViewModel
import com.example.myapplication.feature.shopPicker.model.ShopItem
import com.example.myapplication.feature.shoppingLists.model.parseMoneyAmountToMinorUnits
import com.example.myapplication.feature.shoppingLists.model.toMoneyAmountString
import com.example.myapplication.feature.shoppingLists.repository.ShoppingListsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ShoppingListViewModel(
    private val shoppingListsRepository: ShoppingListsRepository = ShoppingListsRepository(),
) : ViewModel() {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadedShoppingListId: Long? = null

    private val _state = MutableStateFlow(ShoppingListUiState())
    val state: StateFlow<ShoppingListUiState> = _state.asStateFlow()

    fun loadShoppingList(shoppingListId: Long?) {
        if (shoppingListId == null) {
            loadedShoppingListId = null
            _state.update { it.copy(shoppingListId = null, isLoading = false, errorMessage = null) }
            return
        }
        if (loadedShoppingListId == shoppingListId && (_state.value.selectedShop != null || !_state.value.isLoading)) {
            return
        }

        loadedShoppingListId = shoppingListId
        screenScope.launch {
            _state.update {
                it.copy(
                    shoppingListId = shoppingListId,
                    isLoading = true,
                    errorMessage = null,
                )
            }

            val result = shoppingListsRepository.getShoppingList(shoppingListId)
            if (result.isSuccess) {
                val shoppingList = result.getOrThrow()
                _state.update {
                    it.copy(
                        shoppingListId = shoppingList.id,
                        selectedShop = shoppingList.shop,
                        paidAtInput = shoppingList.paidAt,
                        totalInput = shoppingList.totalAmountMinor.toMoneyAmountString(),
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Unable to load shopping list",
                    )
                }
            }
        }
    }

    fun prefillPaidAtIfEmpty(paidAt: String) {
        _state.update { currentState ->
            if (currentState.shoppingListId == null && currentState.paidAtInput.isBlank()) {
                currentState.copy(paidAtInput = paidAt)
            } else {
                currentState
            }
        }
    }

    fun onSelectedShop(shop: ShopItem) {
        _state.update {
            it.copy(
                selectedShop = shop,
                errorMessage = null,
            )
        }
    }

    fun clearSelectedShop() {
        _state.update {
            it.copy(
                selectedShop = null,
                errorMessage = null,
            )
        }
    }

    fun onPaidAtChanged(paidAt: String) {
        _state.update {
            it.copy(
                paidAtInput = paidAt,
                errorMessage = null,
            )
        }
    }

    fun onTotalChanged(total: String) {
        _state.update {
            it.copy(
                totalInput = total,
                errorMessage = null,
            )
        }
    }

    fun consumeSaveSuccess() {
        _state.update { it.copy(saveSucceeded = false) }
    }

    fun saveShoppingList() {
        val currentState = _state.value
        if (currentState.isLoading || currentState.isSaving) {
            return
        }

        val selectedShop = currentState.selectedShop
        if (selectedShop == null) {
            _state.update { it.copy(errorMessage = "Select a shop first") }
            return
        }

        val paidAt = currentState.paidAtInput.trim()
        if (!paidAtPattern.matches(paidAt)) {
            _state.update { it.copy(errorMessage = "Payment time must use yyyy-MM-dd HH:mm") }
            return
        }

        val totalAmountMinor = parseMoneyAmountToMinorUnits(currentState.totalInput)
        if (totalAmountMinor == null) {
            _state.update { it.copy(errorMessage = "Enter a valid total amount") }
            return
        }

        screenScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }

            val result = if (currentState.shoppingListId == null) {
                shoppingListsRepository.createShoppingList(
                    shopId = selectedShop.id,
                    paidAt = paidAt,
                    totalAmountMinor = totalAmountMinor,
                )
            } else {
                shoppingListsRepository.updateShoppingList(
                    id = currentState.shoppingListId,
                    shopId = selectedShop.id,
                    paidAt = paidAt,
                    totalAmountMinor = totalAmountMinor,
                )
            }

            if (result.isSuccess) {
                val savedShoppingList = result.getOrThrow()
                _state.update {
                    it.copy(
                        shoppingListId = savedShoppingList.id,
                        selectedShop = savedShoppingList.shop,
                        paidAtInput = savedShoppingList.paidAt,
                        totalInput = savedShoppingList.totalAmountMinor.toMoneyAmountString(),
                        isSaving = false,
                        errorMessage = null,
                        saveSucceeded = true,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Unable to save shopping list",
                    )
                }
            }
        }
    }

    override fun onCleared() {
        screenScope.cancel()
        super.onCleared()
    }

    private companion object {
        private val paidAtPattern = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")
    }
}
