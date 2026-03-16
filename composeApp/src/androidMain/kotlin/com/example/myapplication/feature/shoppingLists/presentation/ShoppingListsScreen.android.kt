package com.example.myapplication.feature.shoppingLists.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.shopping_lists_cancel_button
import myapplication.composeapp.generated.resources.shopping_lists_delete_button
import myapplication.composeapp.generated.resources.shopping_lists_delete_confirm_message
import myapplication.composeapp.generated.resources.shopping_lists_delete_confirm_title
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun ShoppingListsScreen(
    onCreateShoppingList: () -> Unit,
    onEditShoppingList: (Long) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier,
) {
    val viewModel: ShoppingListsViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    var pendingDeleteShoppingListId by rememberSaveable { mutableStateOf<Long?>(null) }

    pendingDeleteShoppingListId?.let { shoppingListId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteShoppingListId = null },
            title = { Text(text = stringResource(Res.string.shopping_lists_delete_confirm_title)) },
            text = { Text(text = stringResource(Res.string.shopping_lists_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteShoppingListId = null
                        viewModel.deleteShoppingList(shoppingListId)
                    },
                ) {
                    Text(text = stringResource(Res.string.shopping_lists_delete_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteShoppingListId = null }) {
                    Text(text = stringResource(Res.string.shopping_lists_cancel_button))
                }
            },
        )
    }

    ShoppingListsContent(
        state = state,
        onCreateShoppingList = onCreateShoppingList,
        onEditShoppingList = onEditShoppingList,
        onOpenProfile = onOpenProfile,
        onDeleteShoppingList = { pendingDeleteShoppingListId = it },
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}
