package com.example.myapplication.feature.itemCatalog.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.common.ui.RefreshOnResumeEffect
import com.example.myapplication.common.ui.theme.AppTheme
import com.example.myapplication.feature.itemCatalog.model.UnitType
import com.example.myapplication.feature.itemCatalog.model.labelResource
import com.example.myapplication.feature.shoppingList.presentation.PendingItemEntity
import com.example.myapplication.feature.shoppingList.presentation.ShoppingListItemsLocalStore
import com.example.myapplication.feature.shoppingList.presentation.rememberShoppingListItemsLocalStore
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.common_back_button
import myapplication.composeapp.generated.resources.need_barcode_barcode_draft_label
import myapplication.composeapp.generated.resources.need_barcode_empty_message
import myapplication.composeapp.generated.resources.need_barcode_item_subtitle
import myapplication.composeapp.generated.resources.need_barcode_local_only_label
import myapplication.composeapp.generated.resources.need_barcode_search_button
import myapplication.composeapp.generated.resources.need_barcode_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun NeedBarcodeFilledScreen(
    onBack: () -> Unit,
    onOpenItemSelect: (pendingItemId: Long, initialQuery: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val localStore = rememberShoppingListItemsLocalStore()
    val viewModel: NeedBarcodeFilledViewModel = viewModel(
        factory = remember(localStore) {
            needBarcodeFilledViewModelFactory(localStore = localStore)
        },
    )
    val state by viewModel.state.collectAsState()

    RefreshOnResumeEffect(onResume = viewModel::refresh)

    NeedBarcodeFilledContent(
        state = state,
        onBack = onBack,
        onBarcodeDraftChanged = viewModel::onBarcodeDraftChanged,
        onResolve = onOpenItemSelect,
        modifier = modifier,
    )
}

@Composable
internal fun NeedBarcodeFilledContent(
    state: NeedBarcodeFilledUiState,
    onBack: () -> Unit,
    onBarcodeDraftChanged: (Long, String) -> Unit,
    onResolve: (pendingItemId: Long, initialQuery: String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            text = stringResource(Res.string.need_barcode_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        if (state.items.isEmpty()) {
            Text(
                text = stringResource(Res.string.need_barcode_empty_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items, key = PendingItemEntity::id) { item ->
                    PendingItemCard(
                        item = item,
                        onBarcodeDraftChanged = { onBarcodeDraftChanged(item.id, it) },
                        onResolve = { onResolve(item.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingItemCard(
    item: PendingItemEntity,
    onBarcodeDraftChanged: (String) -> Unit,
    onResolve: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = item.mainName,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(
                Res.string.need_barcode_item_subtitle,
                stringResource(item.unit.labelResource()),
                stringResource(Res.string.need_barcode_local_only_label),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = item.barcodeDraft,
            onValueChange = onBarcodeDraftChanged,
            label = { Text(text = stringResource(Res.string.need_barcode_barcode_draft_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onResolve(item.barcodeDraft) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(Res.string.need_barcode_search_button))
        }
    }
}

@Preview
@Composable
private fun NeedBarcodeFilledContentPreview() {
    AppTheme {
        NeedBarcodeFilledContent(
            state = NeedBarcodeFilledUiState(
                items = listOf(
                    PendingItemEntity(
                        id = 1L,
                        mainName = "Potatoes",
                        aliasNames = listOf("Potatoes"),
                        unit = UnitType.KG,
                        barcodeDraft = "",
                    ),
                ),
            ),
            onBack = {},
            onBarcodeDraftChanged = { _, _ -> },
            onResolve = { _, _ -> },
        )
    }
}

@Preview
@Composable
private fun PendingItemCardPreview() {
    AppTheme {
        PendingItemCard(
            item = PendingItemEntity(
                id = 1L,
                mainName = "Potatoes",
                aliasNames = listOf("Potatoes"),
                unit = UnitType.KG,
                barcodeDraft = "",
            ),
            onBarcodeDraftChanged = {},
            onResolve = {},
        )
    }
}

private fun needBarcodeFilledViewModelFactory(
    localStore: ShoppingListItemsLocalStore,
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NeedBarcodeFilledViewModel(localStore = localStore) as T
        }
    }
}
