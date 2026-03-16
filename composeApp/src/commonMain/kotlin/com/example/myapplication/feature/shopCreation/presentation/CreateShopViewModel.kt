package com.example.myapplication.feature.shopCreation.presentation

import androidx.lifecycle.ViewModel
import com.example.myapplication.feature.shopCreation.model.NewShopDraft
import com.example.myapplication.feature.shopPicker.repository.ShopsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateShopViewModel(
    private val shopsRepository: ShopsRepository = ShopsRepository(),
) : ViewModel() {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(CreateShopUiState())
    val state: StateFlow<CreateShopUiState> = _state.asStateFlow()

    fun onNameChanged(name: String) {
        _state.update {
            it.copy(
                name = name,
                errorMessage = null,
            )
        }
    }

    fun onLocationResolved(
        latitude: Double,
        longitude: Double,
        city: String?,
        address: String?,
    ) {
        _state.update {
            it.copy(
                latitude = latitude,
                longitude = longitude,
                city = city.orEmpty(),
                address = address.orEmpty(),
                errorMessage = null,
            )
        }
    }

    fun clearResolvedLocation() {
        _state.update {
            it.copy(
                city = "",
                address = "",
                latitude = null,
                longitude = null,
                errorMessage = null,
            )
        }
    }

    fun consumeCreatedShop() {
        _state.update { it.copy(createdShopName = null) }
    }

    fun saveShop() {
        val currentState = _state.value
        val trimmedName = currentState.name.trim()

        when {
            trimmedName.isEmpty() -> {
                _state.update { it.copy(errorMessage = "Shop name is required") }
                return
            }

            currentState.latitude == null || currentState.longitude == null || currentState.address.isBlank() -> {
                _state.update {
                    it.copy(errorMessage = "Select a shop location from GPS or address search")
                }
                return
            }

            currentState.isSaving -> return
        }

        screenScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }

            val result = shopsRepository.createShop(
                NewShopDraft(
                    name = trimmedName,
                    city = currentState.city.takeIf(String::isNotBlank),
                    address = currentState.address.takeIf(String::isNotBlank),
                    latitude = currentState.latitude,
                    longitude = currentState.longitude,
                ),
            )

            if (result.isSuccess) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = null,
                        createdShopName = result.getOrThrow().name,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Unable to create shop",
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
