package com.example.myapplication.feature.onboarding.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.example.myapplication.common.ui.ImagePlaceholder
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.welcome_continue_button
import myapplication.composeapp.generated.resources.welcome_image_content_description
import myapplication.composeapp.generated.resources.welcome_image_failed
import myapplication.composeapp.generated.resources.welcome_image_loading
import myapplication.composeapp.generated.resources.welcome_message
import myapplication.composeapp.generated.resources.welcome_title
import org.jetbrains.compose.resources.stringResource

private const val WelcomeImageUrl =
    "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1400&q=80"

@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        SubcomposeAsyncImage(
            model = WelcomeImageUrl,
            contentDescription = stringResource(Res.string.welcome_image_content_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(20.dp)),
            loading = {
                ImagePlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    text = stringResource(Res.string.welcome_image_loading),
                )
            },
            error = {
                ImagePlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    text = stringResource(Res.string.welcome_image_failed),
                )
            },
        )
        Text(
            text = stringResource(Res.string.welcome_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(Res.string.welcome_continue_button))
        }
    }
}
