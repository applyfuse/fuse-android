package com.applyfuse.fuse.features.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AuthScreen(
    onNavigateToHome: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthEvent.NavigateToHome -> onNavigateToHome()
                is AuthEvent.NavigateToLogin -> onNavigateToLogin()
                is AuthEvent.ShowToast -> { /* handled by error banner */ }
                else -> Unit
            }
        }
    }

    AuthContent(
        state = state,
        onAction = viewModel::send
    )
}

@Composable
private fun AuthContent(
    state: AuthState,
    onAction: (AuthAction) -> Unit
) {
    val purple = Color(0xFF5D52CC)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF07070D),
                        Color(0xFF0D0D1A)
                    )
                )
            )
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 80.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            AuthHeader()
            Spacer(modifier = Modifier.height(40.dp))
            EmailField(
                email = state.email,
                onEmailChange = { onAction(AuthAction.EmailChanged(it)) }
            )
            Spacer(modifier = Modifier.height(14.dp))
            PasswordField(
                password = state.password,
                onPasswordChange = { onAction(AuthAction.PasswordChanged(it)) },
                onDone = {
                    if (state.canSubmit) {
                        onAction(AuthAction.LoginTapped)
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            AnimatedVisibility(
                visible = state.hasError,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ErrorBanner(
                    message = state.errorMessage ?: "",
                    onDismiss = { onAction(AuthAction.ErrorDismissed) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            LoginButton(
                isLoading = state.isLoading,
                canSubmit = state.canSubmit,
                onClick = { onAction(AuthAction.LoginTapped) },
                purple = purple
            )
        }
    }
}

@Composable
private fun AuthHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "\u26A1", fontSize = 48.sp)
        Text(
            text = "FUSE",
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 8.sp,
            color = Color.White
        )
        Text(
            text = "Sign in to continue",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFB8B8D8)
        )
    }
}

@Composable
private fun EmailField(
    email: String,
    onEmailChange: (String) -> Unit
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PasswordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    onDone: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = if (isVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { isVisible = !isVisible }) {
                Icon(
                    imageVector = if (isVisible) {
                        Icons.Default.VisibilityOff
                    } else {
                        Icons.Default.Visibility
                    },
                    contentDescription = if (isVisible) "Hide password" else "Show password"
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.Red.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "\u26A0\uFE0F", fontSize = 16.sp)
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFB3B3),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Text(text = "\u00D7", color = Color.Gray, fontSize = 18.sp)
        }
    }
}

@Composable
private fun LoginButton(
    isLoading: Boolean,
    canSubmit: Boolean,
    onClick: () -> Unit,
    purple: Color
    // purpleLight removed — was unused; Phase 2 gradient will re-introduce it
) {
    Button(
        onClick = onClick,
        enabled = canSubmit,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = purple,
            disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = "Sign In",
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontSize = 16.sp
            )
        }
    }
}

@Preview(name = "Empty form", showBackground = true, backgroundColor = 0xFF07070D)
@Composable
private fun PreviewEmpty() {
    AuthContent(state = AuthState.Empty, onAction = {})
}

@Preview(name = "Loading", showBackground = true, backgroundColor = 0xFF07070D)
@Composable
private fun PreviewLoading() {
    AuthContent(state = AuthState.Loading, onAction = {})
}

@Preview(name = "Error", showBackground = true, backgroundColor = 0xFF07070D)
@Composable
private fun PreviewError() {
    AuthContent(state = AuthState.Failed, onAction = {})
}

@Preview(name = "Form filled", showBackground = true, backgroundColor = 0xFF07070D)
@Composable
private fun PreviewFilled() {
    AuthContent(
        state = AuthState(email = "muhammad@applyfuse.com", password = "password123"),
        onAction = {}
    )
}
