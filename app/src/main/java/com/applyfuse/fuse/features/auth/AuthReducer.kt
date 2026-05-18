package com.applyfuse.fuse.features.auth

// FUSE: The reducer is the most important file in any feature.
// It is a pure function — given the same state and action,
// it always returns the same result. No network calls,
// no coroutines, no side effects, no dependencies whatsoever.
//
// This makes it:
// - Trivially testable: call it, assert the output, done
// - Completely predictable: every state transition is explicit
// - Easy to debug: if state is wrong, the reducer is wrong
//
// BaseViewModel calls this synchronously inside send()
// before launching any effects in handleEffect().

fun authReducer(state: AuthState, action: AuthAction): AuthState = when (action) {
    // FUSE: Update email on every keystroke.
    // Clear errorMessage so the error banner dismisses as
    // soon as the user starts correcting their input.
    is AuthAction.EmailChanged -> state.copy(
        email = action.email,
        errorMessage = null
    )

    // FUSE: Update password on every keystroke.
    // No error clearing here — password changes don't
    // indicate the user is correcting an email error.
    is AuthAction.PasswordChanged -> state.copy(
        password = action.password
    )

    // FUSE: Mark loading before the effect fires.
    // The UI sees isLoading = true and shows a spinner
    // immediately — before any network call starts.
    // Also clear any previous error so stale messages
    // don't linger while the new request is in flight.
    is AuthAction.LoginTapped -> state.copy(
        isLoading = true,
        errorMessage = null
    )

    // FUSE: Store the authenticated user and stop loading.
    // handleEffect() will emit a NavigateToHome event
    // separately — navigation is never stored in state.
    is AuthAction.LoginSuccess -> state.copy(
        isLoading = false,
        user = action.user,
        errorMessage = null
    )

    // FUSE: Store the human-readable error and stop loading.
    // userMessage lives on AppError — the reducer never
    // constructs strings, it only delegates to the error type.
    is AuthAction.LoginFailure -> state.copy(
        isLoading = false,
        errorMessage = action.error.userMessage
    )

    // FUSE: Full state reset on logout.
    // Return AuthState() to wipe every field at once.
    // This is safer than clearing fields individually —
    // adding a new field to AuthState won't silently leave
    // stale data after logout.
    is AuthAction.LogoutTapped,
    is AuthAction.LogoutSuccess -> AuthState()

    // FUSE: Dismiss the error banner without changing
    // any other state. The user acknowledged the error
    // but hasn't started typing yet.
    is AuthAction.ErrorDismissed -> state.copy(
        errorMessage = null
    )
}
