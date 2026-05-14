package com.applyfuse.fuse.features.auth

import com.applyfuse.fuse.domain.model.User

// FUSE: AuthState is the single source of truth for the
// Auth feature. It is a data class so every change produces
// a new copy via copy() — no accidental shared mutation.
//
// Rules:
// - Every property has a default value — AuthState() is always valid
// - Store only raw values — derived values are computed properties
// - Never store navigation state here — that belongs in AuthEvent
// - data class gives equals() / hashCode() for free —
//   assertEquals(state, expected) works in tests without mocks

data class AuthState(

    // FUSE: Form field values mirror exactly what the user typed.
    // The reducer updates these on every keystroke via
    // EmailChanged and PasswordChanged actions.
    val email: String = "",
    val password: String = "",

    // FUSE: The authenticated user. null means logged out.
    // Set by the reducer on LoginSuccess.
    // Cleared by the reducer on LogoutTapped.
    val user: User? = null,

    // FUSE: isLoading is set to true by the reducer on LoginTapped
    // BEFORE the effect fires. The UI reads this to show a spinner.
    // Set back to false by LoginSuccess or LoginFailure.
    val isLoading: Boolean = false,

    // FUSE: errorMessage is set by the reducer on LoginFailure.
    // Cleared on EmailChanged so the error disappears when
    // the user starts correcting their input.
    val errorMessage: String? = null

) {
    // MARK: — Derived computed properties

    // FUSE: Never store isLoggedIn as a val — it would need
    // to be kept in sync with user manually. A computed
    // property is always correct and adds zero state cost.
    val isLoggedIn: Boolean
        get() = user != null

    // FUSE: canSubmit prevents the login button from being tapped
    // when the form is incomplete or a request is already in flight.
    // The UI binds directly: enabled = state.canSubmit
    val canSubmit: Boolean
        get() = email.isNotBlank()
            && password.isNotEmpty()
            && !isLoading

    // FUSE: hasError is a convenience for the UI to conditionally
    // show the error container without null checks.
    val hasError: Boolean
        get() = errorMessage != null

    companion object {
        // FUSE: State snapshots for Compose @Preview and tests.
        // Use these instead of constructing states manually each time.

        // FUSE: Clean slate — what the screen looks like on first open.
        val Empty = AuthState()

        // FUSE: Simulate a request in flight — test spinner visibility.
        val Loading = AuthState(
            email = "muhammad@applyfuse.com",
            password = "password123",
            isLoading = true
        )

        // FUSE: Simulate a logged-in session — test authenticated UI.
        val LoggedIn = AuthState(
            email = "muhammad@applyfuse.com",
            user = User.mock
        )

        // FUSE: Simulate a failed login — test error display.
        val Failed = AuthState(
            email = "wrong@example.com",
            password = "badpassword",
            errorMessage = "Your session has expired. Please sign in again."
        )
    }
}
