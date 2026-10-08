package com.vintows.app.feature.auth.ui.student

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintows.app.core.designsystem.theme.VintowsTheme

@Composable
fun StudentSignInRoute(
    onBack: () -> Unit,
    onNeedsRegistration: (email: String) -> Unit,
    viewModel: StudentSignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.registerEmail) {
        state.registerEmail?.let {
            viewModel.onRegisterOpened()
            onNeedsRegistration(it)
        }
    }
    StudentSignInScreen(
        state = state,
        onBack = if (state.stage == SignInStage.Code) viewModel::changeEmail else onBack,
        onEmailChange = viewModel::onEmailChange,
        onNameChange = viewModel::onNameChange,
        onSendCode = viewModel::sendCode,
        onCodeChange = viewModel::onCodeChange,
        onVerify = viewModel::verify,
        onResend = viewModel::resend,
        onChangeEmail = viewModel::changeEmail,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentSignInScreen(
    state: StudentSignInUiState,
    onBack: () -> Unit,
    onEmailChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onChangeEmail: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Student sign in") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            when (state.stage) {
                SignInStage.Email -> EmailStep(state, onEmailChange, onNameChange, onSendCode)
                SignInStage.Code -> CodeStep(state, onCodeChange, onVerify, onResend, onChangeEmail)
            }
            if (state.error != null) {
                Text(
                    state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun EmailStep(
    state: StudentSignInUiState,
    onEmailChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onSendCode: () -> Unit,
) {
    Text("Learn. Live. Build.", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
    Text(
        "Enter your email and we'll send you a 4-digit code. New here? You'll create your student profile right after.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = state.email,
        onValueChange = onEmailChange,
        label = { Text("Email") },
        leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
        isError = state.emailError != null,
        supportingText = state.emailError?.let { { Text(it) } },
        singleLine = true,
        enabled = !state.sending,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
    OutlinedTextField(
        value = state.name,
        onValueChange = onNameChange,
        label = { Text("Your name (optional)") },
        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
        singleLine = true,
        enabled = !state.sending,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSendCode() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onSendCode, enabled = !state.sending, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        if (state.sending) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text("Send code")
        }
    }
}

@Composable
private fun CodeStep(
    state: StudentSignInUiState,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onChangeEmail: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Text("Check your email", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
    Text(
        "We sent a 4-digit code to ${state.email.trim()}.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = state.code,
        onValueChange = onCodeChange,
        label = { Text("Verification code") },
        singleLine = true,
        enabled = !state.verifying,
        textStyle = TextStyle(fontSize = 28.sp, letterSpacing = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onVerify() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onVerify,
        enabled = !state.verifying && state.code.length == StudentSignInViewModel.CODE_LENGTH,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        if (state.verifying) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text("Verify")
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onChangeEmail) { Text("Change email") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onResend, enabled = state.resendInSeconds == 0 && !state.sending) {
            Text(if (state.resendInSeconds > 0) "Resend in ${state.resendInSeconds}s" else "Resend code")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CodeStepPreview() {
    VintowsTheme {
        StudentSignInScreen(
            state = StudentSignInUiState(stage = SignInStage.Code, email = "student@example.com", code = "12", resendInSeconds = 42),
            onBack = {}, onEmailChange = {}, onNameChange = {}, onSendCode = {},
            onCodeChange = {}, onVerify = {}, onResend = {}, onChangeEmail = {},
        )
    }
}
