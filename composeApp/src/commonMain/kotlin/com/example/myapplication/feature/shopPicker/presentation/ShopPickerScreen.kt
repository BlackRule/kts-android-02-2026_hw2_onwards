package com.example.myapplication.feature.shopPicker.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
//import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.common.ui.ImagePlaceholder
import com.example.myapplication.common.ui.theme.AppTheme
import com.example.myapplication.common.ui.theme.Dimens
import com.example.myapplication.feature.shopPicker.model.ShopItem
import kotlinx.coroutines.flow.distinctUntilChanged
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.shop_picker_disabled_label
import myapplication.composeapp.generated.resources.shop_picker_empty_image_label
import myapplication.composeapp.generated.resources.shop_picker_empty_message
import myapplication.composeapp.generated.resources.shop_picker_empty_title
import myapplication.composeapp.generated.resources.shop_picker_hours_label
import myapplication.composeapp.generated.resources.shop_picker_load_error
import myapplication.composeapp.generated.resources.shop_picker_load_more_error
import myapplication.composeapp.generated.resources.shop_picker_loading
import myapplication.composeapp.generated.resources.shop_picker_retry_button
import myapplication.composeapp.generated.resources.shop_picker_search_label
import myapplication.composeapp.generated.resources.shop_picker_search_placeholder
import myapplication.composeapp.generated.resources.shop_picker_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun ShopPickerScreen(
    modifier: Modifier = Modifier,
    viewModel: ShopPickerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    ShopPickerContent(
        state = state,
        modifier = modifier,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onRetry = viewModel::retry,
        onLoadNextPage = viewModel::onLoadNextPage,
    )
}

@Composable
private fun ShopPickerContent(
    state: ShopPickerUiState,
    onSearchQueryChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadNextPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val latestState by rememberUpdatedState(state)
    val latestLoadNextPage by rememberUpdatedState(onLoadNextPage)
    val paginationErrorMessage = state.paginationError?.takeIf { it.isNotBlank() }
        ?: stringResource(Res.string.shop_picker_load_more_error)

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                val currentState = latestState
                if (
                    currentState.shops.isNotEmpty() &&
                    lastVisibleIndex >= currentState.shops.lastIndex &&
                    currentState.hasNextPage &&
                    !currentState.isLoading &&
                    !currentState.isLoadingNextPage
                ) {
                    latestLoadNextPage()
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.contentSpacing),
    ) {
        Text(
            text = stringResource(Res.string.shop_picker_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = onSearchQueryChanged,
            label = { Text(text = stringResource(Res.string.shop_picker_search_label)) },
            placeholder = { Text(text = stringResource(Res.string.shop_picker_search_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            state.isLoading && state.shops.isEmpty() -> {
                LoadingState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }

            state.errorMessage != null && state.shops.isEmpty() -> {
                ErrorState(
                    message = state.errorMessage ?: stringResource(Res.string.shop_picker_load_error),
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }

            state.shops.isEmpty() -> {
                EmptyState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(
                        items = state.shops,
                        key = { index, shop -> "${shop.id}-$index" },
                    ) { _, shop ->
                        ShopCard(shop = shop)
                    }

                    if (state.isLoadingNextPage) {
                        item {
                            PaginationLoadingFooter()
                        }
                    }

                    state.paginationError?.let {
                        item {
                            PaginationErrorFooter(
                                message = paginationErrorMessage,
                                onRetry = onLoadNextPage,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopCard(shop: ShopItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = shop.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = shop.city,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = shop.address,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${stringResource(Res.string.shop_picker_hours_label)}: ${shop.openingTime} - ${shop.closingTime}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!shop.enabled) {
                Text(
                    text = stringResource(Res.string.shop_picker_disabled_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(text = stringResource(Res.string.shop_picker_loading))
        }
    }
}

@Composable
private fun ErrorState(
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
                Text(text = stringResource(Res.string.shop_picker_retry_button))
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
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
                text = stringResource(Res.string.shop_picker_empty_image_label),
            )
            Text(
                text = stringResource(Res.string.shop_picker_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.shop_picker_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PaginationLoadingFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun PaginationErrorFooter(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) {
            Text(text = stringResource(Res.string.shop_picker_retry_button))
        }
    }
}

@Preview
@Composable
private fun ShopPickerScreenPreview() {
    AppTheme {
        ShopPickerContent(
            state = ShopPickerUiState(
                query = "spar",
                shops = listOf(
                    ShopItem(
                        id = 60L,
                        name = "Универсам \"Спар №60\"",
                        city = "Калининград г",
                        openingTime = "08:00",
                        closingTime = "23:00",
                        lat = 54.74686,
                        lon = 20.48272,
                        address = "SPAR на Елизаветинской, 11",
                        enabled = true,
                    ),
                ),
                currentPage = 1,
                hasNextPage = true,
            ),
            onSearchQueryChanged = {},
            onRetry = {},
            onLoadNextPage = {},
        )
    }
}
