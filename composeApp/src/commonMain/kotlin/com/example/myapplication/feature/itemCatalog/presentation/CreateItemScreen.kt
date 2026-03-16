package com.example.myapplication.feature.itemCatalog.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.common.ui.asString
import com.example.myapplication.common.ui.rememberPlatformMessageHandler
import com.example.myapplication.common.ui.resolveString
import com.example.myapplication.common.ui.theme.AppTheme
import com.example.myapplication.feature.itemCatalog.model.UnitType
import com.example.myapplication.feature.itemCatalog.model.labelResource
import com.example.myapplication.feature.shoppingList.presentation.ShoppingListItemsLocalStore
import com.example.myapplication.feature.shoppingList.presentation.rememberShoppingListItemsLocalStore
import kotlinx.coroutines.flow.collect
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.common_back_button
import myapplication.composeapp.generated.resources.create_item_barcode_label
import myapplication.composeapp.generated.resources.create_item_description
import myapplication.composeapp.generated.resources.create_item_main_name_label
import myapplication.composeapp.generated.resources.create_item_save_button
import myapplication.composeapp.generated.resources.create_item_saving_button
import myapplication.composeapp.generated.resources.create_item_title
import myapplication.composeapp.generated.resources.create_item_unit_label
import org.jetbrains.compose.resources.stringResource

@Composable
fun CreateItemScreen(
    ownerKey: Long?,
    rowId: Long?,
    pendingItemId: Long?,
    initialQuery: String,
    soldByWeight: Boolean,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val localStore = rememberShoppingListItemsLocalStore()
    val showMessage = rememberPlatformMessageHandler()
    val viewModel: CreateItemViewModel = viewModel(
        factory = remember(
            ownerKey,
            rowId,
            pendingItemId,
            initialQuery,
            soldByWeight,
            localStore,
        ) {
            createItemViewModelFactory(
                ownerKey = ownerKey,
                rowId = rowId,
                pendingItemId = pendingItemId,
                initialQuery = initialQuery,
                soldByWeight = soldByWeight,
                localStore = localStore,
            )
        },
    )
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, showMessage, onFinished) {
        viewModel.events.collect { event ->
            when (event) {
                is CreateItemEvent.ShowMessage -> showMessage(event.message.resolveString())
                CreateItemEvent.Finished -> onFinished()
            }
        }
    }

    CreateItemContent(
        state = state,
        onBack = onBack,
        onBarcodeChanged = viewModel::onBarcodeChanged,
        onMainNameChanged = viewModel::onMainNameChanged,
        onUnitSelected = viewModel::onUnitSelected,
        onSave = viewModel::saveItem,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateItemContent(
    state: CreateItemUiState,
    onBack: () -> Unit,
    onBarcodeChanged: (String) -> Unit,
    onMainNameChanged: (String) -> Unit,
    onUnitSelected: (UnitType) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nameSuggestionsExpanded by rememberSaveable { mutableStateOf(false) }
    var unitExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(text = stringResource(Res.string.common_back_button))
        }

        Text(
            text = stringResource(Res.string.create_item_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        Text(
            text = stringResource(Res.string.create_item_description),
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = state.barcodeInput,
            onValueChange = onBarcodeChanged,
            label = { Text(text = stringResource(Res.string.create_item_barcode_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        ExposedDropdownMenuBox(
            expanded = nameSuggestionsExpanded,
            onExpandedChange = { nameSuggestionsExpanded = !nameSuggestionsExpanded },
        ) {
            OutlinedTextField(
                value = state.mainNameInput,
                onValueChange = onMainNameChanged,
                label = { Text(text = stringResource(Res.string.create_item_main_name_label)) },
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = nameSuggestionsExpanded)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            DropdownMenu(
                expanded = nameSuggestionsExpanded && state.filteredSuggestionNames.isNotEmpty(),
                onDismissRequest = { nameSuggestionsExpanded = false },
            ) {
                state.filteredSuggestionNames.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(text = suggestion) },
                        onClick = {
                            onMainNameChanged(suggestion)
                            nameSuggestionsExpanded = false
                        },
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = unitExpanded,
            onExpandedChange = { unitExpanded = !unitExpanded },
        ) {
            OutlinedTextField(
                value = stringResource(state.selectedUnit.labelResource()),
                onValueChange = {},
                readOnly = true,
                label = { Text(text = stringResource(Res.string.create_item_unit_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            DropdownMenu(
                expanded = unitExpanded,
                onDismissRequest = { unitExpanded = false },
            ) {
                UnitType.entries.forEach { unit ->
                    DropdownMenuItem(
                        text = { Text(text = stringResource(unit.labelResource())) },
                        onClick = {
                            onUnitSelected(unit)
                            unitExpanded = false
                        },
                    )
                }
            }
        }

        state.errorMessage?.let { message ->
            Text(
                text = message.asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onSave,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (state.isSaving) {
                    stringResource(Res.string.create_item_saving_button)
                } else {
                    stringResource(Res.string.create_item_save_button)
                },
            )
        }
    }
}

@Preview
@Composable
private fun CreateItemContentPreview() {
    AppTheme {
        CreateItemContent(
            state = CreateItemUiState(
                barcodeInput = "2200000000011",
                mainNameInput = "Bananas",
                suggestionNames = listOf("Bananas", "Banana premium"),
                selectedUnit = UnitType.KG,
            ),
            onBack = {},
            onBarcodeChanged = {},
            onMainNameChanged = {},
            onUnitSelected = {},
            onSave = {},
        )
    }
}

private fun createItemViewModelFactory(
    ownerKey: Long?,
    rowId: Long?,
    pendingItemId: Long?,
    initialQuery: String,
    soldByWeight: Boolean,
    localStore: ShoppingListItemsLocalStore,
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CreateItemViewModel(
                ownerKey = ownerKey,
                rowId = rowId,
                pendingItemId = pendingItemId,
                initialQuery = initialQuery,
                soldByWeight = soldByWeight,
                localStore = localStore,
            ) as T
        }
    }
}
