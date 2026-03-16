package com.example.myapplication.feature.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.common.ui.theme.AppTheme
import com.example.myapplication.common.ui.theme.Dimens
import com.example.myapplication.core.di.LocalAppContainer
import com.example.myapplication.feature.profile.model.LoggedInProfile
import kotlinx.coroutines.launch
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.common_back_button
import myapplication.composeapp.generated.resources.profile_full_name_label
import myapplication.composeapp.generated.resources.profile_logout_button
import myapplication.composeapp.generated.resources.profile_logout_error
import myapplication.composeapp.generated.resources.profile_position_label
import myapplication.composeapp.generated.resources.profile_title
import myapplication.composeapp.generated.resources.profile_unavailable_message
import myapplication.composeapp.generated.resources.profile_username_label
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContainer = LocalAppContainer.current
    val sessionState by appContainer.sessionRepository.sessionState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val logoutErrorText = stringResource(Res.string.profile_logout_error)

    var isLoggingOut by remember { mutableStateOf(false) }
    var logoutError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.contentSpacing),
    ) {
        TextButton(
            onClick = onBack,
        ) {
            Text(text = stringResource(Res.string.common_back_button))
        }

        Text(
            text = stringResource(Res.string.profile_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        sessionState.profile?.let { profile ->
            ProfileInfoCard(profile = profile)
        } ?: Text(
            text = stringResource(Res.string.profile_unavailable_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        logoutError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            enabled = !isLoggingOut,
            onClick = {
                if (isLoggingOut) {
                    return@Button
                }
                coroutineScope.launch {
                    isLoggingOut = true
                    logoutError = null
                    runCatching {
                        appContainer.appDataCleaner.clear()
                        appContainer.sessionRepository.clearSession()
                    }.onSuccess {
                        onLoggedOut()
                    }.onFailure { exception ->
                        logoutError = exception.message ?: logoutErrorText
                        isLoggingOut = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(Res.string.profile_logout_button))
        }
    }
}

@Composable
private fun ProfileInfoCard(
    profile: LoggedInProfile,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileField(
                label = stringResource(Res.string.profile_username_label),
                value = profile.username,
            )
            ProfileField(
                label = stringResource(Res.string.profile_full_name_label),
                value = profile.fullName,
            )
            ProfileField(
                label = stringResource(Res.string.profile_position_label),
                value = profile.position,
            )
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview
@Composable
private fun ProfileInfoCardPreview() {
    AppTheme {
        ProfileInfoCard(
            profile = LoggedInProfile(
                id = 2L,
                username = "admin",
                fullName = "Liam Johnson",
                position = "Android Engineer",
            ),
        )
    }
}
