package com.example.myapplication.feature.shopPicker.presentation

import androidx.lifecycle.ViewModel
import com.example.myapplication.DEFAULT_SHOPS_PAGE_SIZE
import com.example.myapplication.core.location.GeoPoint
import com.example.myapplication.feature.shopPicker.model.ShopItem
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
    private var userLocation: GeoPoint? = null

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

    fun onUserLocationChanged(location: GeoPoint) {
        userLocation = location
        _state.update { state ->
            val presentation = arrangeShops(state.shops)
            state.copy(
                shops = presentation.shops,
                closestShopId = presentation.closestShopId,
            )
        }
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
                    val presentation = arrangeShops(state.shops + nextPage.shops)
                    state.copy(
                        shops = presentation.shops,
                        closestShopId = presentation.closestShopId,
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
                            closestShopId = null,
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
                val presentation = arrangeShops(page.shops)
                it.copy(
                    shops = presentation.shops,
                    closestShopId = presentation.closestShopId,
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
                    closestShopId = null,
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

    private fun arrangeShops(shops: List<ShopItem>): ShopPresentation {
        val location = userLocation ?: return ShopPresentation(
            shops = shops,
            closestShopId = null,
        )

        val closestShopIndex = shops.indices.minByOrNull { index ->
            shops[index].distanceTo(location) ?: Double.MAX_VALUE
        } ?: return ShopPresentation(
            shops = shops,
            closestShopId = null,
        )

        val closestShop = shops.getOrNull(closestShopIndex)
            ?.takeIf { it.distanceTo(location) != null }
            ?: return ShopPresentation(
                shops = shops,
                closestShopId = null,
            )

        return ShopPresentation(
            shops = buildList {
                add(closestShop)
                shops.forEachIndexed { index, shop ->
                    if (index != closestShopIndex) {
                        add(shop)
                    }
                }
            },
            closestShopId = closestShop.id,
        )
    }

    private companion object {
        private const val FIRST_PAGE = 1
        private const val SEARCH_DEBOUNCE_MILLIS = 400L
    }
}

private data class ShopPresentation(
    val shops: List<ShopItem>,
    val closestShopId: Long?,
)

private fun ShopItem.distanceTo(location: GeoPoint): Double? {
    val shopLat = lat ?: return null
    val shopLon = lon ?: return null
    return haversineDistanceMeters(
        startLatitude = location.latitude,
        startLongitude = location.longitude,
        endLatitude = shopLat,
        endLongitude = shopLon,
    )
}

private fun haversineDistanceMeters(
    startLatitude: Double,
    startLongitude: Double,
    endLatitude: Double,
    endLongitude: Double,
): Double {
    val earthRadiusMeters = 6_371_000.0
    val latitudeDelta = (endLatitude - startLatitude).toRadians()
    val longitudeDelta = (endLongitude - startLongitude).toRadians()
    val startLatitudeRadians = startLatitude.toRadians()
    val endLatitudeRadians = endLatitude.toRadians()

    val a = kotlin.math.sin(latitudeDelta / 2).let { it * it } +
        kotlin.math.cos(startLatitudeRadians) *
        kotlin.math.cos(endLatitudeRadians) *
        kotlin.math.sin(longitudeDelta / 2).let { it * it }
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return earthRadiusMeters * c
}

private fun Double.toRadians(): Double = this * kotlin.math.PI / 180.0
