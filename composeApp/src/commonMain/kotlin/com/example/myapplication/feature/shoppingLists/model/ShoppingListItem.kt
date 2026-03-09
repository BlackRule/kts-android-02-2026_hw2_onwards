package com.example.myapplication.feature.shoppingLists.model

import com.example.myapplication.feature.shopPicker.model.ShopItem

data class ShoppingListItem(
    val id: Long,
    val shop: ShopItem,
    val paidAt: String,
    val totalAmountMinor: Long,
)

fun Long.toMoneyAmountString(): String {
    val wholePart = this / 100L
    val fractionalPart = (this % 100L).toString().padStart(2, '0')
    return "$wholePart.$fractionalPart"
}

fun parseMoneyAmountToMinorUnits(input: String): Long? {
    val normalizedInput = input.trim().replace(',', '.')
    val moneyPattern = Regex("""\d+(\.\d{1,2})?""")
    if (!moneyPattern.matches(normalizedInput)) {
        return null
    }

    val parts = normalizedInput.split('.')
    val wholePart = parts[0].toLongOrNull() ?: return null
    val fractionalPart = when (parts.getOrNull(1)?.length ?: 0) {
        0 -> 0L
        1 -> parts[1].plus("0").toLongOrNull() ?: return null
        2 -> parts[1].toLongOrNull() ?: return null
        else -> return null
    }

    return (wholePart * 100L) + fractionalPart
}
