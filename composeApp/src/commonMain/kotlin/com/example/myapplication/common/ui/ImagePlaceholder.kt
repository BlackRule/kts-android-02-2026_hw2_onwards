package com.example.myapplication.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.common.ui.theme.AppTheme
import com.example.myapplication.common.ui.theme.Dimens
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.image_unavailable
import org.jetbrains.compose.resources.stringResource

@Composable
fun ImagePlaceholder(
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    val displayText = text ?: stringResource(Res.string.image_unavailable)

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(Dimens.thinBorder, MaterialTheme.colorScheme.outlineVariant)
            .padding(Dimens.placeholderPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun ImagePlaceholderPreview() {
    AppTheme {
        ImagePlaceholder(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        )
    }
}
