package com.example.myapplication.feature.shoppingList.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.common.ui.theme.Dimens
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.shopping_list_back_button
import myapplication.composeapp.generated.resources.shopping_list_checkout_time_label
import myapplication.composeapp.generated.resources.shopping_list_checkout_time_placeholder
import myapplication.composeapp.generated.resources.shopping_list_clear_shop_button
import myapplication.composeapp.generated.resources.shopping_list_pick_time_button
import myapplication.composeapp.generated.resources.shopping_list_save_button
import myapplication.composeapp.generated.resources.shopping_list_saving_button
import myapplication.composeapp.generated.resources.shopping_list_select_shop_button
import myapplication.composeapp.generated.resources.shopping_list_selected_shop_label
import myapplication.composeapp.generated.resources.shopping_list_title_create
import myapplication.composeapp.generated.resources.shopping_list_title_edit
import myapplication.composeapp.generated.resources.shopping_list_total_label
import myapplication.composeapp.generated.resources.shopping_list_total_placeholder
import myapplication.composeapp.generated.resources.shopping_list_use_current_time_button
import org.jetbrains.compose.resources.stringResource

@Composable
expect fun ShoppingListScreen(
    shoppingListId: Long?,
    selectedShopPayload: String?,
    onSelectedShopConsumed: () -> Unit,
    onBack: () -> Unit,
    onSelectShop: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
internal fun ShoppingListContent(
    state: ShoppingListUiState,
    onBack: () -> Unit,
    onSelectShop: () -> Unit,
    onClearShop: () -> Unit,
    onPaidAtChanged: (String) -> Unit,
    onPickPaidAt: () -> Unit,
    onUseCurrentTime: () -> Unit,
    onTotalChanged: (String) -> Unit,
    onSave: () -> Unit,
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
            value = state.totalInput,
            onValueChange = onTotalChanged,
            label = { Text(text = stringResource(Res.string.shopping_list_total_label)) },
            placeholder = { Text(text = stringResource(Res.string.shopping_list_total_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        state.errorMessage?.let { errorMessage ->
            Text(
                text = errorMessage,
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
