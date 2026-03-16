package com.example.myapplication.feature.itemCatalog.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.common.ui.asString
import com.example.myapplication.common.ui.theme.AppTheme
import com.example.myapplication.feature.itemCatalog.model.ItemLookupMatch
import com.example.myapplication.feature.itemCatalog.model.UnitType
import com.example.myapplication.feature.itemCatalog.model.labelResource
import com.example.myapplication.feature.shoppingList.presentation.ShoppingListItemsLocalStore
import com.example.myapplication.feature.shoppingList.presentation.rememberShoppingListItemsLocalStore
import kotlinx.coroutines.flow.collect
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.common_back_button
import myapplication.composeapp.generated.resources.item_select_camera_button
import myapplication.composeapp.generated.resources.item_select_match_subtitle
import myapplication.composeapp.generated.resources.item_select_pick_one_label
import myapplication.composeapp.generated.resources.item_select_query_label
import myapplication.composeapp.generated.resources.item_select_query_placeholder
import myapplication.composeapp.generated.resources.item_select_search_button
import myapplication.composeapp.generated.resources.item_select_sold_by_weight_label
import myapplication.composeapp.generated.resources.item_select_title
import myapplication.composeapp.generated.resources.item_select_use_selected_button
import org.jetbrains.compose.resources.stringResource

@Composable
fun ItemSelectScreen(
    ownerKey: Long?,
    rowId: Long?,
    pendingItemId: Long?,
    initialQuery: String,
    onBack: () -> Unit,
    onOpenCreateItem: (
        ownerKey: Long?,
        rowId: Long?,
        pendingItemId: Long?,
        initialQuery: String,
        soldByWeight: Boolean,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val localStore = rememberShoppingListItemsLocalStore()
    val viewModel: ItemSelectViewModel = viewModel(
        factory = remember(localStore, ownerKey, rowId, pendingItemId, initialQuery) {
            itemSelectViewModelFactory(
                ownerKey = ownerKey,
                rowId = rowId,
                pendingItemId = pendingItemId,
                initialQuery = initialQuery,
                localStore = localStore,
            )
        },
    )
    val state by viewModel.state.collectAsState()
    val cameraPermissionState = rememberCameraPermissionState()

    LaunchedEffect(viewModel, ownerKey, rowId, pendingItemId, onBack, onOpenCreateItem) {
        viewModel.events.collect { event ->
            when (event) {
                is ItemSelectEvent.OpenCreateItem -> {
                    onOpenCreateItem(
                        ownerKey,
                        rowId,
                        pendingItemId,
                        event.initialQuery,
                        event.soldByWeight,
                    )
                }

                ItemSelectEvent.NavigateBack -> onBack()
            }
        }
    }

    LaunchedEffect(cameraPermissionState) {
        cameraPermissionState.requestPermission()
    }

    ItemSelectContent(
        state = state,
        cameraPermissionGranted = cameraPermissionState.isGranted,
        onBack = onBack,
        onRequestCameraPermission = cameraPermissionState::requestPermission,
        onQueryChanged = viewModel::onQueryChanged,
        onSoldByWeightChanged = viewModel::onSoldByWeightChanged,
        onSearch = viewModel::onSearchRequested,
        onMatchSelected = viewModel::onMatchSelected,
        onUseSelectedMatch = viewModel::onUseSelectedMatch,
        onBarcodeScanned = viewModel::onBarcodeScanned,
        modifier = modifier,
    )
}

@Composable
internal fun ItemSelectContent(
    state: ItemSelectUiState,
    cameraPermissionGranted: Boolean,
    onBack: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSoldByWeightChanged: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onMatchSelected: (Int) -> Unit,
    onUseSelectedMatch: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) {
                Text(text = stringResource(Res.string.common_back_button))
            }

            Text(
                text = stringResource(Res.string.item_select_title),
                style = MaterialTheme.typography.headlineMedium,
            )

            if (cameraPermissionGranted) {
                BarcodeScannerPreview(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    onBarcodeScanned = onBarcodeScanned,
                )
            } else {
                Button(onClick = onRequestCameraPermission) {
                    Text(text = stringResource(Res.string.item_select_camera_button))
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                label = { Text(text = stringResource(Res.string.item_select_query_label)) },
                placeholder = { Text(text = stringResource(Res.string.item_select_query_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSoldByWeightChanged(!state.soldByWeight) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = state.soldByWeight,
                    onCheckedChange = null,
                )
                Text(text = stringResource(Res.string.item_select_sold_by_weight_label))
            }

            Button(
                onClick = onSearch,
                enabled = !state.isSearching,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(Res.string.item_select_search_button))
            }

            if (state.isSearching) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                }
            }

            state.errorMessage?.let { message ->
                Text(
                    text = message.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (state.matches.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.item_select_pick_one_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(state.matches) { index, match ->
                        MatchRow(
                            match = match,
                            selected = index == state.selectedIndex,
                            onClick = { onMatchSelected(index) },
                        )
                    }
                }
                Button(
                    onClick = onUseSelectedMatch,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(Res.string.item_select_use_selected_button))
                }
            }
        }
    }
}

@Composable
private fun MatchRow(
    match: ItemLookupMatch,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        tonalElevation = if (selected) 2.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = highlightedMatchText(match.matchedName, match.highlightRanges),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(
                        Res.string.item_select_match_subtitle,
                        stringResource(match.item.unit.labelResource()),
                        match.item.barcode,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun ItemSelectContentPreview() {
    AppTheme {
        ItemSelectContent(
            state = ItemSelectUiState(
                query = "banana",
                matches = listOf(
                    ItemLookupMatch(
                        item = com.example.myapplication.feature.itemCatalog.model.CatalogItem(
                            barcode = "2200000000011",
                            mainName = "Bananas",
                            names = listOf("Bananas"),
                            unit = UnitType.KG,
                        ),
                        matchedName = "Bananas",
                        highlightRanges = listOf(),
                    ),
                ),
            ),
            cameraPermissionGranted = false,
            onBack = {},
            onRequestCameraPermission = {},
            onQueryChanged = {},
            onSoldByWeightChanged = {},
            onSearch = {},
            onMatchSelected = {},
            onUseSelectedMatch = {},
            onBarcodeScanned = {},
        )
    }
}

private fun itemSelectViewModelFactory(
    ownerKey: Long?,
    rowId: Long?,
    pendingItemId: Long?,
    initialQuery: String,
    localStore: ShoppingListItemsLocalStore,
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ItemSelectViewModel(
                ownerKey = ownerKey,
                rowId = rowId,
                pendingItemId = pendingItemId,
                initialQuery = initialQuery,
                localStore = localStore,
            ) as T
        }
    }
}
