package com.example.myapplication.feature.shopPicker.presentation

import androidx.lifecycle.ViewModel
import com.example.myapplication.DEFAULT_SHOPS_PAGE_SIZE
import com.example.myapplication.feature.shopPicker.model.ShopsPage
import com.example.myapplication.feature.shopPicker.repository.ShopsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ShopPickerViewModel(
    private val shopsRepository: ShopsRepository = ShopsRepository(),
) : ViewModel() {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val searchQuery = MutableStateFlow("")
    private val refreshRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)

    private val _state = MutableStateFlow(ShopPickerUiState())
    val state: StateFlow<ShopPickerUiState> = _state.asStateFlow()

    private var activeQuery: String = ""
    private var loadMoreJob: Job? = null

    init {
        observeSearchRequests()
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(query = query) }
        searchQuery.value = query
    }

    fun retry() {
        refreshRequests.tryEmit(_state.value.query.trim())
    }

    fun onLoadNextPage() {
        val currentState = _state.value
        if (currentState.isLoading || currentState.isLoadingNextPage || !currentState.hasNextPage) {
            return
        }

        loadMoreJob?.cancel()
        loadMoreJob = screenScope.launch {
            _state.update { it.copy(isLoadingNextPage = true, paginationError = null) }

            val result = shopsRepository.getShops(
                query = activeQuery,
                page = currentState.currentPage + 1,
                pageSize = DEFAULT_SHOPS_PAGE_SIZE,
            )

            if (result.isSuccess) {
                val nextPage = result.getOrThrow()
                _state.update { state ->
                    state.copy(
                        shops = state.shops + nextPage.shops,
                        isLoadingNextPage = false,
                        paginationError = null,
                        currentPage = nextPage.page,
                        hasNextPage = nextPage.hasNextPage,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isLoadingNextPage = false,
                        paginationError = result.exceptionOrNull()?.message,
                    )
                }
            }
        }
    }

    private fun observeSearchRequests() {
        screenScope.launch {
            merge(
                searchQuery
                    .debounce(SEARCH_DEBOUNCE_MILLIS)
                    .map { it.trim() }
                    .distinctUntilChanged(),
                refreshRequests,
            )
                .onEach { query ->
                    activeQuery = query
                    loadMoreJob?.cancel()
                    _state.update {
                        it.copy(
                            isLoading = true,
                            errorMessage = null,
                            paginationError = null,
                            isLoadingNextPage = false,
                            shops = emptyList(),
                            currentPage = 0,
                            hasNextPage = false,
                        )
                    }
                }
                .flatMapLatest { query ->
                    flow {
                        emit(
                            query to shopsRepository.getShops(
                                query = query,
                                page = FIRST_PAGE,
                                pageSize = DEFAULT_SHOPS_PAGE_SIZE,
                            ),
                        )
                    }
                }
                .collect { (query, result) ->
                    handleFirstPageResult(
                        query = query,
                        result = result,
                    )
                }
        }
    }

    private fun handleFirstPageResult(
        query: String,
        result: Result<ShopsPage>,
    ) {
        activeQuery = query
        if (result.isSuccess) {
            val page = result.getOrThrow()
            _state.update {
                it.copy(
                    shops = page.shops,
                    isLoading = false,
                    errorMessage = null,
                    currentPage = page.page,
                    hasNextPage = page.hasNextPage,
                    paginationError = null,
                    isLoadingNextPage = false,
                )
            }
        } else {
            _state.update {
                it.copy(
                    shops = emptyList(),
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    currentPage = 0,
                    hasNextPage = false,
                    paginationError = null,
                    isLoadingNextPage = false,
                )
            }
        }
    }

    override fun onCleared() {
        loadMoreJob?.cancel()
        screenScope.cancel()
        super.onCleared()
    }

    private companion object {
        private const val FIRST_PAGE = 1
        private const val SEARCH_DEBOUNCE_MILLIS = 400L
    }
}
