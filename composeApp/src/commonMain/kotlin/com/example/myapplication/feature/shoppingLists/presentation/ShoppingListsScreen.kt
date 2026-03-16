package com.example.myapplication.feature.shoppingLists.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.common.ui.ImagePlaceholder
import com.example.myapplication.common.ui.theme.Dimens
import com.example.myapplication.feature.shoppingLists.model.ShoppingListItem
import com.example.myapplication.feature.shoppingLists.model.toMoneyAmountString
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.shopping_lists_add_button
import myapplication.composeapp.generated.resources.shopping_lists_delete_button
import myapplication.composeapp.generated.resources.shopping_lists_edit_button
import myapplication.composeapp.generated.resources.shopping_lists_empty_image_label
import myapplication.composeapp.generated.resources.shopping_lists_empty_message
import myapplication.composeapp.generated.resources.shopping_lists_empty_title
import myapplication.composeapp.generated.resources.shopping_lists_loading
import myapplication.composeapp.generated.resources.shopping_lists_paid_at_label
import myapplication.composeapp.generated.resources.shopping_lists_profile_button
import myapplication.composeapp.generated.resources.shopping_lists_retry_button
import myapplication.composeapp.generated.resources.shopping_lists_title
import myapplication.composeapp.generated.resources.shopping_lists_total_label
import org.jetbrains.compose.resources.stringResource

@Composable
expect fun ShoppingListsScreen(
    onCreateShoppingList: () -> Unit,
    onEditShoppingList: (Long) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
internal fun ShoppingListsContent(
    state: ShoppingListsUiState,
    onCreateShoppingList: () -> Unit,
    onEditShoppingList: (Long) -> Unit,
    onOpenProfile: () -> Unit,
    onDeleteShoppingList: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.shopping_lists_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenProfile) {
                    Text(text = stringResource(Res.string.shopping_lists_profile_button))
                }
                Button(onClick = onCreateShoppingList) {
                    Text(text = stringResource(Res.string.shopping_lists_add_button))
                }
            }
        }

        when {
            state.isLoading -> {
                ShoppingListsLoadingState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }

            state.errorMessage != null && state.shoppingLists.isEmpty() -> {
                ShoppingListsErrorState(
                    message = state.errorMessage,
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }

            state.shoppingLists.isEmpty() -> {
                ShoppingListsEmptyState(
                    onCreateShoppingList = onCreateShoppingList,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }

            else -> {
                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = state.shoppingLists,
                        key = ShoppingListItem::id,
                    ) { shoppingList ->
                        ShoppingListCard(
                            shoppingList = shoppingList,
                            isDeleting = state.deletingShoppingListId == shoppingList.id,
                            onEditShoppingList = { onEditShoppingList(shoppingList.id) },
                            onDeleteShoppingList = { onDeleteShoppingList(shoppingList.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingListCard(
    shoppingList: ShoppingListItem,
    isDeleting: Boolean,
    onEditShoppingList: () -> Unit,
    onDeleteShoppingList: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = shoppingList.shop.name,
                style = MaterialTheme.typography.titleMedium,
            )
            shoppingList.shop.city?.takeIf { it.isNotBlank() }?.let { city ->
                Text(
                    text = city,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            shoppingList.shop.address?.takeIf { it.isNotBlank() }?.let { address ->
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "${stringResource(Res.string.shopping_lists_paid_at_label)}: ${shoppingList.paidAt}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${stringResource(Res.string.shopping_lists_total_label)}: ${shoppingList.totalAmountMinor.toMoneyAmountString()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onEditShoppingList) {
                    Text(text = stringResource(Res.string.shopping_lists_edit_button))
                }
                TextButton(
                    onClick = onDeleteShoppingList,
                    enabled = !isDeleting,
                ) {
                    Text(text = stringResource(Res.string.shopping_lists_delete_button))
                }
            }
        }
    }
}

@Composable
private fun ShoppingListsLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(text = stringResource(Res.string.shopping_lists_loading))
        }
    }
}

@Composable
private fun ShoppingListsErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onRetry) {
                Text(text = stringResource(Res.string.shopping_lists_retry_button))
            }
        }
    }
}

@Composable
private fun ShoppingListsEmptyState(
    onCreateShoppingList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ImagePlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                text = stringResource(Res.string.shopping_lists_empty_image_label),
            )
            Text(
                text = stringResource(Res.string.shopping_lists_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.shopping_lists_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onCreateShoppingList) {
                Text(text = stringResource(Res.string.shopping_lists_add_button))
            }
        }
    }
}
