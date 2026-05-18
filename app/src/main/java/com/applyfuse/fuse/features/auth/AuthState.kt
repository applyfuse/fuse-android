package com.applyfuse.fuse.features.auth

import com.applyfuse.fuse.domain.model.User

// FUSE: AuthState is the single source of truth for the
// Auth feature. It is a data class so every change produces
// a new copy via copy() — no accidental shared mutation.

data class AuthState(
    val email: String = "",
    val password: String = "",
    val user: User? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val isLoggedIn: Boolean
        get() = user != null

    // FUSE: canSubmit — && operator placed at end of line per ChainWrapping rule.
    val canSubmit: Boolean
        get() = email.isNotBlank() &&
            password.isNotEmpty() &&
            !isLoading

    val hasError: Boolean
        get() = errorMessage != null

    companion object {
        val Empty = AuthState()

        val Loading = AuthState(
            email = "muhammad@applyfuse.com",
            password = "password123",
            isLoading = true
        )

        val LoggedIn = AuthState(
            email = "muhammad@applyfuse.com",
            user = User.mock
        )

        val Failed = AuthState(
            email = "wrong@example.com",
            password = "badpassword",
            errorMessage = "Your session has expired. Please sign in again."
        )
    }
}
