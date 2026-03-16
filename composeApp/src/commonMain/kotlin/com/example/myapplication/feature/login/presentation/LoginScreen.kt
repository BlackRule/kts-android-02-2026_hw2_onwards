package com.example.myapplication.feature.login.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.common.ui.asString
import com.example.myapplication.common.ui.theme.AppTheme
import com.example.myapplication.common.ui.theme.Dimens
import com.example.myapplication.core.di.LocalAppContainer
import com.example.myapplication.core.di.ProvideAppContainer
import com.example.myapplication.core.session.SessionRepository
import com.example.myapplication.feature.login.repository.LoginRepository
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.login_password_label
import myapplication.composeapp.generated.resources.login_password_placeholder
import myapplication.composeapp.generated.resources.login_sign_in_button
import myapplication.composeapp.generated.resources.login_title
import myapplication.composeapp.generated.resources.login_username_label
import myapplication.composeapp.generated.resources.login_username_placeholder
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.flow.collect

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContainer = LocalAppContainer.current
    val viewModel: LoginViewModel = viewModel(
        factory = remember(
            appContainer.loginRepository,
            appContainer.sessionRepository,
        ) {
            loginViewModelFactory(
                loginRepository = appContainer.loginRepository,
                sessionRepository = appContainer.sessionRepository,
            )
        },
    )
    val state by viewModel.state.collectAsState()
    val autofillManager = LocalAutofillManager.current

    LaunchedEffect(viewModel, autofillManager, onLoginSuccess) {
        viewModel.events.collect { event ->
            when (event) {
                LoginUiEvent.LoginSuccessEvent -> {
                    autofillManager?.commit()
                    onLoginSuccess()
                }
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
            text = stringResource(Res.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        TextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChanged,
            label = { Text(text = stringResource(Res.string.login_username_label)) },
            placeholder = { Text(text = stringResource(Res.string.login_username_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .contentType(ContentType.Username + ContentType.NewUsername),
        )
        TextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChanged,
            label = { Text(text = stringResource(Res.string.login_password_label)) },
            placeholder = { Text(text = stringResource(Res.string.login_password_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .contentType(ContentType.Password + ContentType.NewPassword),
        )
        state.error?.let { errorText ->
            Text(
                text = errorText.asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            enabled = state.isLoginButtonActive,
            onClick = viewModel::onLoginClicked,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(Res.string.login_sign_in_button))
        }
    }
}

@Preview
@Composable
private fun LoginScreenPreview() {
    AppTheme {
        ProvideAppContainer {
            LoginScreen(onLoginSuccess = {})
        }
    }
}

private fun loginViewModelFactory(
    loginRepository: LoginRepository,
    sessionRepository: SessionRepository,
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LoginViewModel(
                loginRepository = loginRepository,
                sessionRepository = sessionRepository,
            ) as T
        }
    }
}
