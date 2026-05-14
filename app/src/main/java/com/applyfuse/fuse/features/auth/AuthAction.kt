package com.applyfuse.fuse.features.auth

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.domain.model.User

// FUSE: AuthAction is the complete vocabulary of the Auth feature.
// Every possible thing that can happen — user intent or system
// response — is listed here as a sealed class.
//
// Rules:
// - If something can happen, it MUST be an Action
// - If it’s not in this sealed class, the reducer can’t handle it
// - Split into user intents and system responses for clarity
// - Data classes carry the values the reducer needs
// - Objects are used for actions with no associated data

sealed class AuthAction {

    // MARK: — User intents
    // FUSE: These are things the user explicitly does.
    // Sent from the Composable via viewModel.send()

    // FUSE: Fired on every keystroke in the email field.
    // The reducer updates state.email and clears any error.
    data class EmailChanged(val email: String) : AuthAction()

    // FUSE: Fired on every keystroke in the password field.
    data class PasswordChanged(val password: String) : AuthAction()

    // FUSE: Fired when the user taps the Login button.
    // The reducer sets isLoading = true.
    // The ViewModel then fires the async login effect.
    object LoginTapped : AuthAction()

    // FUSE: Fired when the user taps Logout.
    // The reducer resets state to AuthState().
    object LogoutTapped : AuthAction()

    // FUSE: Fired when the user taps the error dismiss button
    // or navigates away from the error state.
    object ErrorDismissed : AuthAction()

    // MARK: — System responses
    // FUSE: These are the results of async effects.
    // They are sent back into the ViewModel by handleEffect(),
    // never directly by the Composable.

    // FUSE: Sent by handleEffect() after a successful login.
    // Carries the authenticated User so the reducer can store it.
    data class LoginSuccess(val user: User) : AuthAction()

    // FUSE: Sent by handleEffect() after a failed login.
    // Carries the typed AppError so the reducer can extract
    // the user-facing message via error.userMessage.
    data class LoginFailure(val error: AppError) : AuthAction()

    // FUSE: Sent by handleEffect() after logout completes.
    object LogoutSuccess : AuthAction()
}

// FUSE: AuthEvent carries one-time navigation and UI events.
// These go through SharedFlow — NOT through state.
// They fire exactly once and are never replayed.
sealed class AuthEvent : com.applyfuse.fuse.core.FuseEvent {

    // FUSE: Navigate to the home screen after successful login.
    object NavigateToHome : AuthEvent()

    // FUSE: Navigate back to login after logout.
    object NavigateToLogin : AuthEvent()

    // FUSE: Show a toast with a message.
    data class ShowToast(val message: String) : AuthEvent()
}
