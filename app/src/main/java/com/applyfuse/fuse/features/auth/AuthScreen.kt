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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// FUSE: AuthScreen is a pure rendering function.
// It reads from state and calls vm.send() on interactions.
// It never mutates state directly.
//
// Navigation events are collected in LaunchedEffect — NOT by
// observing state.isLoggedIn in an if/else. SharedFlow fires
// exactly once; state observation would re-fire on every
// recomposition that sees isLoggedIn = true.

@Composable
fun AuthScreen(
    // FUSE: Navigation callbacks injected from the NavHost.
    // The screen doesn’t know what "navigate to home" means —
    // it fires the callback and the NavHost decides.
    onNavigateToHome: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    // FUSE: collectAsStateWithLifecycle() is lifecycle-aware.
    // It stops collecting when the screen is not visible,
    // preventing unnecessary recompositions in the background.
    val state by viewModel.state.collectAsStateWithLifecycle()

    // FUSE: LaunchedEffect(Unit) runs once when the composable
    // enters composition and cancels when it leaves.
    // Collecting SharedFlow here means events are received
    // exactly once — no replay, no duplication.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthEvent.NavigateToHome  -> onNavigateToHome()
                is AuthEvent.NavigateToLogin -> onNavigateToLogin()
                is AuthEvent.ShowToast       -> { /* handled by error banner */ }
                else                         -> Unit
            }
        }
    }

    AuthContent(
        state = state,
        onAction = viewModel::send
    )
}

// FUSE: AuthContent is a stateless composable — it only takes
// state and a callback. This makes it trivially previewable
// and testable without a ViewModel.
@Composable
private fun AuthContent(
    state: AuthState,
    onAction: (AuthAction) -> Unit
) {
    val purple = Color(0xFF5D52CC)
    val purpleLight = Color(0xFF7B6EF6)

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
            // Header
            AuthHeader()
            Spacer(modifier = Modifier.height(40.dp))

            // Form fields
            EmailField(
                email = state.email,
                onEmailChange = { onAction(AuthAction.EmailChanged(it)) }
            )
            Spacer(modifier = Modifier.height(14.dp))
            PasswordField(
                password = state.password,
                onPasswordChange = { onAction(AuthAction.PasswordChanged(it)) },
                onDone = { if (state.canSubmit) onAction(AuthAction.LoginTapped) }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // FUSE: AnimatedVisibility wraps the error banner.
            // It appears and disappears with animation when
            // state.hasError changes — no manual show/hide logic.
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

            // Login button
            LoginButton(
                isLoading = state.isLoading,
                canSubmit = state.canSubmit,
                onClick = { onAction(AuthAction.LoginTapped) },
                purple = purple,
                purpleLight = purpleLight
            )
        }
    }
}

// MARK: — Header

@Composable
private fun AuthHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "\u26A1",
            fontSize = 48.sp
        )
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

// MARK: — Email field

@Composable
private fun EmailField(
    email: String,
    onEmailChange: (String) -> Unit
) {
    OutlinedTextField(
        value = email,
        // FUSE: onValueChange sends an action — never mutates
        // a local variable. The state comes back down via
        // collectAsStateWithLifecycle().
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

// MARK: — Password field

@Composable
private fun PasswordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    onDone: () -> Unit
) {
    // FUSE: isVisible is purely presentational — it controls
    // whether the password characters are hidden or shown.
    // It does NOT belong in AuthState because it has zero
    // business logic significance. Local compose state is
    // the correct home for pure UI-only state.
    var isVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = if (isVisible)
            VisualTransformation.None
        else
            PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { isVisible = !isVisible }) {
                Icon(
                    imageVector = if (isVisible)
                        Icons.Default.VisibilityOff
                    else
                        Icons.Default.Visibility,
                    contentDescription = if (isVisible)
                        "Hide password" else "Show password"
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

// MARK: — Error banner

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
        Text(
            text = "\u26A0️",
            fontSize = 16.sp
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFB3B3),
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(24.dp)
        ) {
            Text(text = "\u00D7", color = Color.Gray, fontSize = 18.sp)
        }
    }
}

// MARK: — Login button

@Composable
private fun LoginButton(
    isLoading: Boolean,
    canSubmit: Boolean,
    onClick: () -> Unit,
    purple: Color,
    purpleLight: Color
) {
    Button(
        onClick = onClick,
        // FUSE: enabled reads directly from canSubmit — a computed
        // property on AuthState. The button is automatically disabled
        // when email/password are empty OR isLoading is true.
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

// MARK: — Previews

// FUSE: Each preview passes a state snapshot directly to
// AuthContent — the stateless composable. No ViewModel,
// no Hilt, no coroutines needed in previews.

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
