package com.example.myapplication.feature.shoppingList.presentation

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
actual fun rememberShoppingListScreenController(shoppingListId: Long?): ShoppingListScreenController {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val viewModel: ShoppingListScreenViewModel = viewModel(
        factory = remember(applicationContext, shoppingListId) {
            shoppingListScreenViewModelFactory(
                shoppingListId = shoppingListId,
                localStore = shoppingListItemsLocalStore(applicationContext),
            )
        },
    )
    return viewModel
}

@Composable
actual fun rememberPaidAtPickerLauncher(onPaidAtSelected: (String) -> Unit): (String) -> Unit {
    val context = LocalContext.current
    val latestOnPaidAtSelected by rememberUpdatedState(onPaidAtSelected)

    return remember(context) {
        { initialPaidAt ->
            showPaidAtPicker(
                context = context,
                initialPaidAt = initialPaidAt,
                onPaidAtSelected = latestOnPaidAtSelected,
            )
        }
    }
}

private fun shoppingListScreenViewModelFactory(
    shoppingListId: Long?,
    localStore: ShoppingListItemsLocalStore,
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ShoppingListScreenViewModel(
                shoppingListId = shoppingListId,
                localStore = localStore,
            ) as T
        }
    }
}

private fun showPaidAtPicker(
    context: android.content.Context,
    initialPaidAt: String,
    onPaidAtSelected: (String) -> Unit,
) {
    val initialDateTime = parsePaidAt(initialPaidAt) ?: LocalDateTime.now()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    onPaidAtSelected(
                        LocalDateTime.of(year, month + 1, dayOfMonth, hourOfDay, minute)
                            .format(paidAtFormatter),
                    )
                },
                initialDateTime.hour,
                initialDateTime.minute,
                true,
            ).show()
        },
        initialDateTime.year,
        initialDateTime.monthValue - 1,
        initialDateTime.dayOfMonth,
    ).show()
}

private fun parsePaidAt(value: String): LocalDateTime? {
    return runCatching {
        LocalDateTime.parse(value.trim(), paidAtFormatter)
    }.getOrNull()
}

private val paidAtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
