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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.myapplication.common.ui.theme.Dimens
import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.login_email_label
import myapplication.composeapp.generated.resources.login_email_placeholder
import myapplication.composeapp.generated.resources.login_password_label
import myapplication.composeapp.generated.resources.login_password_placeholder
import myapplication.composeapp.generated.resources.login_sign_in_button
import myapplication.composeapp.generated.resources.login_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
) {
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

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
            value = login,
            onValueChange = { login = it },
            label = { Text(text = stringResource(Res.string.login_email_label)) },
            placeholder = { Text(text = stringResource(Res.string.login_email_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(text = stringResource(Res.string.login_password_label)) },
            placeholder = { Text(text = stringResource(Res.string.login_password_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = { },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(Res.string.login_sign_in_button))
        }
    }
}
