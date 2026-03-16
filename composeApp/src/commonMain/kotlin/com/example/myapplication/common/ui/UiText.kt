package com.example.myapplication.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

sealed interface UiText {
    data class Dynamic(val value: String) : UiText

    data class Resource(
        val value: StringResource,
        val args: List<Any> = emptyList(),
    ) : UiText
}

fun Throwable?.toUiTextOr(fallback: StringResource): UiText {
    val message = this?.message?.trim().orEmpty()
    return if (message.isNotEmpty()) {
        UiText.Dynamic(message)
    } else {
        UiText.Resource(fallback)
    }
}

@Composable
fun UiText.asString(): String {
    return when (this) {
        is UiText.Dynamic -> value
        is UiText.Resource -> stringResource(value, *args.toTypedArray())
    }
}

suspend fun UiText.resolveString(): String {
    return when (this) {
        is UiText.Dynamic -> value
        is UiText.Resource -> getString(value, *args.toTypedArray())
    }
}

@Preview
@Composable
private fun UiTextPreview() {
    Text(text = UiText.Dynamic("").asString())
}
