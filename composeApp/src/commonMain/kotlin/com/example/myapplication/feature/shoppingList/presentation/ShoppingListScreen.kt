package com.example.myapplication.feature.shoppingList.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.common.ui.asString
import com.example.myapplication.common.ui.rememberPlatformMessageHandler
import com.example.myapplication.common.ui.resolveString
import com.example.myapplication.common.ui.theme.AppTheme
import com.example.myapplication.common.ui.theme.Dimens
import com.example.myapplication.feature.itemCatalog.model.UnitType
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.common_cancel_button
import myapplication.composeapp.generated.resources.common_confirm_button
import myapplication.composeapp.generated.resources.shopping_list_back_button
import myapplication.composeapp.generated.resources.shopping_list_checkout_time_label
import myapplication.composeapp.generated.resources.shopping_list_checkout_time_placeholder
import myapplication.composeapp.generated.resources.shopping_list_clear_shop_button
import myapplication.composeapp.generated.resources.common_inline_separator
import myapplication.composeapp.generated.resources.shopping_list_paid_confirm_title
import myapplication.composeapp.generated.resources.shopping_list_mark_as_paid_button
import myapplication.composeapp.generated.resources.shopping_list_need_barcode_button
import myapplication.composeapp.generated.resources.shopping_list_pick_time_button
import myapplication.composeapp.generated.resources.shopping_list_row_amount_header
import myapplication.composeapp.generated.resources.shopping_list_row_discount_header
import myapplication.composeapp.generated.resources.shopping_list_row_final_price_header
import myapplication.composeapp.generated.resources.shopping_list_row_item_header
import myapplication.composeapp.generated.resources.shopping_list_row_price_header
import myapplication.composeapp.generated.resources.shopping_list_row_total_header
import myapplication.composeapp.generated.resources.shopping_list_rows_add_button
import myapplication.composeapp.generated.resources.shopping_list_save_button
import myapplication.composeapp.generated.resources.shopping_list_saving_button
import myapplication.composeapp.generated.resources.shopping_list_select_item_button
import myapplication.composeapp.generated.resources.shopping_list_select_shop_button
import myapplication.composeapp.generated.resources.shopping_list_selected_shop_label
import myapplication.composeapp.generated.resources.shopping_list_table_hint
import myapplication.composeapp.generated.resources.shopping_list_title_create
import myapplication.composeapp.generated.resources.shopping_list_title_edit
import myapplication.composeapp.generated.resources.shopping_list_total_label
import myapplication.composeapp.generated.resources.shopping_list_total_placeholder
import myapplication.composeapp.generated.resources.shopping_list_unresolved_item_label
import myapplication.composeapp.generated.resources.shopping_list_use_current_time_button
import org.jetbrains.compose.resources.stringResource
import com.example.myapplication.feature.itemCatalog.model.labelResource
import kotlinx.coroutines.flow.collect

@Composable
fun ShoppingListScreen(
    shoppingListId: Long?,
    selectedShopPayload: String?,
    onSelectedShopConsumed: () -> Unit,
    onBack: () -> Unit,
    onSelectShop: () -> Unit,
    onOpenItemSelect: (ownerKey: Long, rowId: Long?) -> Unit,
    onOpenNeedBarcode: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberShoppingListScreenController(shoppingListId)
    val showMessage = rememberPlatformMessageHandler()
    val paidAtPicker = rememberPaidAtPickerLauncher(viewModel::onPaidAtChanged)
    val screenState by viewModel.state.collectAsState()

    LaunchedEffect(selectedShopPayload, viewModel) {
        if (selectedShopPayload != null) {
            viewModel.onSelectedShopPayload(selectedShopPayload)
            onSelectedShopConsumed()
        }
    }

    LaunchedEffect(viewModel, showMessage, onOpenItemSelect, onOpenNeedBarcode, onSaved) {
        viewModel.events.collect { event ->
            when (event) {
                is ShoppingListScreenEvent.OpenItemSelect -> {
                    onOpenItemSelect(event.ownerKey, event.rowId)
                }

                ShoppingListScreenEvent.OpenNeedBarcode -> onOpenNeedBarcode()

                is ShoppingListScreenEvent.ShowMessage -> {
                    showMessage(event.message.resolveString())
                }

                ShoppingListScreenEvent.Saved -> onSaved()
            }
        }
    }

    ShoppingListContent(
        state = screenState.effectiveFormState,
        shoppingRows = screenState.rows,
        unresolvedCount = screenState.unresolvedCount,
        onBack = onBack,
        onSelectShop = onSelectShop,
        onClearShop = viewModel::clearSelectedShop,
        onPaidAtChanged = viewModel::onPaidAtChanged,
        onPickPaidAt = {
            paidAtPicker(screenState.effectiveFormState.paidAtInput)
        },
        onUseCurrentTime = viewModel::useCurrentTime,
        onAddRow = viewModel::onAddRowRequested,
        onSelectRowItem = viewModel::onRowItemRequested,
        onRowPriceChanged = viewModel::onRowPriceChanged,
        onRowDiscountChanged = viewModel::onRowDiscountChanged,
        onRowFinalPriceChanged = viewModel::onRowFinalPriceChanged,
        onRowAmountChanged = viewModel::onRowAmountChanged,
        onSave = viewModel::saveAndExit,
        onMarkAsPaid = viewModel::requestMarkAsPaid,
        onOpenNeedBarcode = onOpenNeedBarcode,
        modifier = modifier,
    )

    if (screenState.showPaidConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPaidConfirmation,
            title = {
                Text(
                    text = stringResource(Res.string.shopping_list_paid_confirm_title),
                )
            },
            text = {
                screenState.paidConfirmationMessage?.let { message ->
                    Text(text = message.asString())
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmMarkAsPaid) {
                    Text(
                        text = stringResource(Res.string.common_confirm_button),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPaidConfirmation) {
                    Text(
                        text = stringResource(Res.string.common_cancel_button),
                    )
                }
            },
        )
    }
}

@Composable
internal fun ShoppingListContent(
    state: ShoppingListUiState,
    shoppingRows: List<ShoppingListEntryUiState>,
    unresolvedCount: Int,
    onBack: () -> Unit,
    onSelectShop: () -> Unit,
    onClearShop: () -> Unit,
    onPaidAtChanged: (String) -> Unit,
    onPickPaidAt: () -> Unit,
    onUseCurrentTime: () -> Unit,
    onAddRow: () -> Unit,
    onSelectRowItem: (Int) -> Unit,
    onRowPriceChanged: (Int, String) -> Unit,
    onRowDiscountChanged: (Int, String) -> Unit,
    onRowFinalPriceChanged: (Int, String) -> Unit,
    onRowAmountChanged: (Int, String) -> Unit,
    onSave: () -> Unit,
    onMarkAsPaid: () -> Unit,
    onOpenNeedBarcode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(text = stringResource(Res.string.shopping_list_back_button))
        }

        Text(
            text = if (state.shoppingListId == null) {
                stringResource(Res.string.shopping_list_title_create)
            } else {
                stringResource(Res.string.shopping_list_title_edit)
            },
            style = MaterialTheme.typography.headlineMedium,
        )

        if (state.isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        Text(
            text = stringResource(Res.string.shopping_list_selected_shop_label),
            style = MaterialTheme.typography.titleMedium,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val selectedShop = state.selectedShop
                if (selectedShop == null) {
                    Text(
                        text = stringResource(Res.string.shopping_list_select_shop_button),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = selectedShop.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    selectedShop.city?.takeIf { it.isNotBlank() }?.let { city ->
                        Text(
                            text = city,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    selectedShop.address?.takeIf { it.isNotBlank() }?.let { address ->
                        Text(
                            text = address,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onSelectShop,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(Res.string.shopping_list_select_shop_button))
                    }
                    TextButton(
                        onClick = onClearShop,
                        enabled = state.selectedShop != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(Res.string.shopping_list_clear_shop_button))
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.paidAtInput,
            onValueChange = onPaidAtChanged,
            label = { Text(text = stringResource(Res.string.shopping_list_checkout_time_label)) },
            placeholder = { Text(text = stringResource(Res.string.shopping_list_checkout_time_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onPickPaidAt,
                enabled = !state.isSaving,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(Res.string.shopping_list_pick_time_button))
            }
            Button(
                onClick = onUseCurrentTime,
                enabled = !state.isSaving,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(Res.string.shopping_list_use_current_time_button))
            }
        }

        OutlinedTextField(
            value = state.totalDisplay,
            onValueChange = {},
            label = { Text(text = stringResource(Res.string.shopping_list_total_label)) },
            placeholder = { Text(text = stringResource(Res.string.shopping_list_total_placeholder)) },
            singleLine = true,
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(Res.string.shopping_list_table_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShoppingTableHeader()
                shoppingRows.forEachIndexed { index, row ->
                    ShoppingTableRow(
                        row = row,
                        onItemClick = { onSelectRowItem(index) },
                        onPriceChanged = { onRowPriceChanged(index, it) },
                        onDiscountChanged = { onRowDiscountChanged(index, it) },
                        onFinalPriceChanged = { onRowFinalPriceChanged(index, it) },
                        onAmountChanged = { onRowAmountChanged(index, it) },
                    )
                }
            }
        }

        Button(
            onClick = onAddRow,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(Res.string.shopping_list_rows_add_button))
        }

        if (unresolvedCount > 0) {
            Button(
                onClick = onOpenNeedBarcode,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(Res.string.shopping_list_need_barcode_button))
            }
        }

        Button(
            onClick = onMarkAsPaid,
            enabled = !state.isLoading && !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(Res.string.shopping_list_mark_as_paid_button))
        }

        state.errorMessage?.let { errorMessage ->
            Text(
                text = errorMessage.asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onSave,
            enabled = !state.isLoading && !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (state.isSaving) {
                    stringResource(Res.string.shopping_list_saving_button)
                } else {
                    stringResource(Res.string.shopping_list_save_button)
                },
            )
        }
    }
}

@Preview
@Composable
private fun ShoppingListContentPreview() {
    AppTheme {
        ShoppingListContent(
            state = ShoppingListUiState(
                selectedShop = com.example.myapplication.feature.shopPicker.model.ShopItem(
                    id = 1L,
                    name = "Fresh Market",
                    city = "Kaliningrad",
                    openingTime = "08:00",
                    closingTime = "22:00",
                    address = "12 River St",
                    enabled = true,
                ),
                paidAtInput = "2026-03-14 18:45",
                totalDisplay = "16.50",
                isLoading = false,
            ),
            shoppingRows = listOf(
                ShoppingListEntryUiState(
                    localId = 1L,
                    itemMainName = "Bananas",
                    unit = UnitType.KG,
                    finalPriceInput = "3.30",
                    amountInput = "2",
                    totalDisplay = "6.60",
                ),
            ),
            unresolvedCount = 0,
            onBack = {},
            onSelectShop = {},
            onClearShop = {},
            onPaidAtChanged = {},
            onPickPaidAt = {},
            onUseCurrentTime = {},
            onAddRow = {},
            onSelectRowItem = {},
            onRowPriceChanged = { _, _ -> },
            onRowDiscountChanged = { _, _ -> },
            onRowFinalPriceChanged = { _, _ -> },
            onRowAmountChanged = { _, _ -> },
            onSave = {},
            onMarkAsPaid = {},
            onOpenNeedBarcode = {},
        )
    }
}

@Composable
private fun ShoppingTableHeader() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TableHeaderCell(
            label = stringResource(Res.string.shopping_list_row_item_header),
            width = 200.dp,
        )
        TableHeaderCell(
            label = stringResource(Res.string.shopping_list_row_price_header),
            width = 110.dp,
        )
        TableHeaderCell(
            label = stringResource(Res.string.shopping_list_row_discount_header),
            width = 110.dp,
        )
        TableHeaderCell(
            label = stringResource(Res.string.shopping_list_row_final_price_header),
            width = 130.dp,
        )
        TableHeaderCell(
            label = stringResource(Res.string.shopping_list_row_amount_header),
            width = 110.dp,
        )
        TableHeaderCell(
            label = stringResource(Res.string.shopping_list_row_total_header),
            width = 110.dp,
        )
    }
}

@Composable
private fun ShoppingTableRow(
    row: ShoppingListEntryUiState,
    onItemClick: () -> Unit,
    onPriceChanged: (String) -> Unit,
    onDiscountChanged: (String) -> Unit,
    onFinalPriceChanged: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ItemCell(
            name = row.itemMainName,
            unit = row.unit,
            isPending = row.pendingItemId != null || row.itemBarcode.isNullOrBlank(),
            onClick = onItemClick,
            width = 200.dp,
        )
        TableInputCell(
            value = row.priceInput,
            onValueChanged = onPriceChanged,
            width = 110.dp,
        )
        TableInputCell(
            value = row.discountPercentInput,
            onValueChanged = onDiscountChanged,
            width = 110.dp,
        )
        TableInputCell(
            value = row.finalPriceInput,
            onValueChanged = onFinalPriceChanged,
            width = 130.dp,
        )
        TableInputCell(
            value = row.amountInput,
            onValueChanged = onAmountChanged,
            width = 110.dp,
        )
        TableValueCell(
            value = row.totalDisplay,
            width = 110.dp,
        )
    }
}

@Composable
private fun TableHeaderCell(
    label: String,
    width: Dp,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .width(width)
            .defaultMinSize(minHeight = 24.dp),
    )
}

@Composable
private fun TableInputCell(
    value: String,
    onValueChanged: (String) -> Unit,
    width: Dp,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        singleLine = true,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun TableValueCell(
    value: String,
    width: Dp,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun ItemCell(
    name: String,
    unit: UnitType?,
    isPending: Boolean,
    onClick: () -> Unit,
    width: Dp,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.width(width),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = name.ifBlank { stringResource(Res.string.shopping_list_select_item_button) },
                style = MaterialTheme.typography.bodyMedium,
            )
            val separator = stringResource(Res.string.common_inline_separator)
            val subtitle = buildList {
                unit?.let { add(stringResource(it.labelResource())) }
                if (isPending) {
                    add(stringResource(Res.string.shopping_list_unresolved_item_label))
                }
            }.joinToString(separator = separator)
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun ShoppingTableHeaderPreview() {
    AppTheme {
        ShoppingTableHeader()
    }
}

@Preview
@Composable
private fun ShoppingTableRowPreview() {
    AppTheme {
        ShoppingTableRow(
            row = ShoppingListEntryUiState(
                localId = 1L,
                itemMainName = "Bananas",
                unit = UnitType.KG,
                priceInput = "4.20",
                discountPercentInput = "10",
                finalPriceInput = "3.78",
                amountInput = "2",
                totalDisplay = "7.56",
            ),
            onItemClick = {},
            onPriceChanged = {},
            onDiscountChanged = {},
            onFinalPriceChanged = {},
            onAmountChanged = {},
        )
    }
}

@Preview
@Composable
private fun ItemCellPreview() {
    AppTheme {
        ItemCell(
            name = "Bananas",
            unit = UnitType.KG,
            isPending = true,
            onClick = {},
            width = 200.dp,
        )
    }
}
