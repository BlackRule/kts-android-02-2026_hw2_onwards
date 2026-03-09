package com.example.myapplication.feature.shoppingLists.presentation

import androidx.lifecycle.ViewModel
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

class ShoppingListsViewModel(
    private val shoppingListsRepository: ShoppingListsRepository = ShoppingListsRepository(),
) : ViewModel() {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(ShoppingListsUiState())
    val state: StateFlow<ShoppingListsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        screenScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            val result = shoppingListsRepository.getShoppingLists()
            if (result.isSuccess) {
                _state.update {
                    it.copy(
                        shoppingLists = result.getOrThrow(),
                        isLoading = false,
                        deletingShoppingListId = null,
                        errorMessage = null,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        deletingShoppingListId = null,
                        errorMessage = result.exceptionOrNull()?.message ?: "Unable to load shopping lists",
                    )
                }
            }
        }
    }

    fun deleteShoppingList(id: Long) {
        if (_state.value.deletingShoppingListId != null) {
            return
        }

        screenScope.launch {
            _state.update {
                it.copy(
                    deletingShoppingListId = id,
                    errorMessage = null,
                )
            }

            val result = shoppingListsRepository.deleteShoppingList(id)
            if (result.isSuccess) {
                _state.update { currentState ->
                    currentState.copy(
                        shoppingLists = currentState.shoppingLists.filterNot { it.id == id },
                        deletingShoppingListId = null,
                        errorMessage = null,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        deletingShoppingListId = null,
                        errorMessage = result.exceptionOrNull()?.message ?: "Unable to delete shopping list",
                    )
                }
            }
        }
    }

    override fun onCleared() {
        screenScope.cancel()
        super.onCleared()
    }
}
