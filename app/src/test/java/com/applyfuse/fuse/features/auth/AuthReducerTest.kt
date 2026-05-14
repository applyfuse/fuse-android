package com.applyfuse.fuse.features.auth

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.domain.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

// FUSE: Reducer tests are the most valuable tests in the codebase.
// They require:
// - Zero mocks
// - Zero coroutines
// - Zero Android framework
// - Zero UI
//
// Pattern for every test:
// 1. Arrange — create a starting state
// 2. Act     — call authReducer(state, action)
// 3. Assert  — check the resulting state
//
// If a test fails here, the reducer logic is wrong.
// Nothing else could have caused it.

@DisplayName("AuthReducer")
class AuthReducerTest {

    private val mockUser = User.mock

    // MARK: — EmailChanged

    @Nested
    @DisplayName("EmailChanged")
    inner class EmailChangedTests {

        @Test
        fun `updates email`() {
            val result = authReducer(AuthState(), AuthAction.EmailChanged("hello@test.com"))
            assertEquals("hello@test.com", result.email)
        }

        @Test
        fun `clears error message`() {
            // FUSE: Error should disappear as soon as the user
            // starts correcting their email.
            val state = AuthState(errorMessage = "Invalid credentials")
            val result = authReducer(state, AuthAction.EmailChanged("new@test.com"))
            assertNull(result.errorMessage)
        }

        @Test
        fun `does not affect other fields`() {
            val state = AuthState(password = "secret")
            val result = authReducer(state, AuthAction.EmailChanged("a@b.com"))
            assertEquals("secret", result.password)
            assertFalse(result.isLoading)
        }
    }

    // MARK: — PasswordChanged

    @Nested
    @DisplayName("PasswordChanged")
    inner class PasswordChangedTests {

        @Test
        fun `updates password`() {
            val result = authReducer(AuthState(), AuthAction.PasswordChanged("newpass"))
            assertEquals("newpass", result.password)
        }

        @Test
        fun `does not clear error`() {
            // FUSE: Password change should NOT clear the error —
            // only email change signals correction of login details.
            val state = AuthState(errorMessage = "Invalid credentials")
            val result = authReducer(state, AuthAction.PasswordChanged("newpass"))
            assertEquals("Invalid credentials", result.errorMessage)
        }
    }

    // MARK: — LoginTapped

    @Nested
    @DisplayName("LoginTapped")
    inner class LoginTappedTests {

        @Test
        fun `sets isLoading to true`() {
            val result = authReducer(AuthState(), AuthAction.LoginTapped)
            assertTrue(result.isLoading)
        }

        @Test
        fun `clears existing error`() {
            // FUSE: Retrying after an error should clear the previous
            // message before the new request completes.
            val state = AuthState(errorMessage = "Previous error")
            val result = authReducer(state, AuthAction.LoginTapped)
            assertNull(result.errorMessage)
        }

        @Test
        fun `does not change user or email`() {
            val state = AuthState(email = "a@b.com", password = "pass")
            val result = authReducer(state, AuthAction.LoginTapped)
            assertEquals("a@b.com", result.email)
            assertEquals("pass", result.password)
            assertNull(result.user)
        }
    }

    // MARK: — LoginSuccess

    @Nested
    @DisplayName("LoginSuccess")
    inner class LoginSuccessTests {

        @Test
        fun `stores user`() {
            val state = AuthState(isLoading = true)
            val result = authReducer(state, AuthAction.LoginSuccess(mockUser))
            assertEquals(mockUser, result.user)
        }

        @Test
        fun `clears loading`() {
            val state = AuthState(isLoading = true)
            val result = authReducer(state, AuthAction.LoginSuccess(mockUser))
            assertFalse(result.isLoading)
        }

        @Test
        fun `clears error`() {
            val state = AuthState(isLoading = true, errorMessage = "old error")
            val result = authReducer(state, AuthAction.LoginSuccess(mockUser))
            assertNull(result.errorMessage)
        }

        @Test
        fun `sets isLoggedIn to true`() {
            val state = AuthState()
            assertFalse(state.isLoggedIn)
            val result = authReducer(state, AuthAction.LoginSuccess(mockUser))
            assertTrue(result.isLoggedIn)
        }
    }

    // MARK: — LoginFailure

    @Nested
    @DisplayName("LoginFailure")
    inner class LoginFailureTests {

        @Test
        fun `sets error message`() {
            val state = AuthState(isLoading = true)
            val result = authReducer(state, AuthAction.LoginFailure(AppError.Unauthorized))
            assertNotNull(result.errorMessage)
            assertEquals(AppError.Unauthorized.userMessage, result.errorMessage)
        }

        @Test
        fun `clears loading`() {
            val state = AuthState(isLoading = true)
            val result = authReducer(state, AuthAction.LoginFailure(AppError.NetworkUnavailable))
            assertFalse(result.isLoading)
        }

        @Test
        fun `does not clear existing user`() {
            // FUSE: If a user is already logged in and a secondary
            // request fails, the existing session must not be cleared.
            val state = AuthState(user = mockUser, isLoading = true)
            val result = authReducer(state, AuthAction.LoginFailure(AppError.ServerError(503)))
            assertEquals(mockUser, result.user)
        }

        @Test
        fun `sets correct message for network error`() {
            val state = AuthState(isLoading = true)
            val result = authReducer(state, AuthAction.LoginFailure(AppError.NetworkUnavailable))
            assertEquals(AppError.NetworkUnavailable.userMessage, result.errorMessage)
        }
    }

    // MARK: — LogoutTapped

    @Nested
    @DisplayName("LogoutTapped")
    inner class LogoutTappedTests {

        @Test
        fun `resets state to default`() {
            val state = AuthState(
                email = "a@b.com",
                password = "pass",
                user = mockUser
            )
            val result = authReducer(state, AuthAction.LogoutTapped)
            assertEquals(AuthState(), result)
        }

        @Test
        fun `clears user and sets isLoggedIn false`() {
            val state = AuthState(user = mockUser)
            val result = authReducer(state, AuthAction.LogoutTapped)
            assertNull(result.user)
            assertFalse(result.isLoggedIn)
        }
    }

    // MARK: — LogoutSuccess

    @Nested
    @DisplayName("LogoutSuccess")
    inner class LogoutSuccessTests {

        @Test
        fun `resets state to default`() {
            val state = AuthState(user = mockUser, email = "a@b.com")
            val result = authReducer(state, AuthAction.LogoutSuccess)
            assertEquals(AuthState(), result)
        }
    }

    // MARK: — ErrorDismissed

    @Nested
    @DisplayName("ErrorDismissed")
    inner class ErrorDismissedTests {

        @Test
        fun `clears error message`() {
            val state = AuthState(errorMessage = "Something went wrong")
            val result = authReducer(state, AuthAction.ErrorDismissed)
            assertNull(result.errorMessage)
        }

        @Test
        fun `does not affect other fields`() {
            val state = AuthState(
                email = "a@b.com",
                password = "pass",
                errorMessage = "error"
            )
            val result = authReducer(state, AuthAction.ErrorDismissed)
            assertEquals("a@b.com", result.email)
            assertEquals("pass", result.password)
        }
    }

    // MARK: — Derived state

    @Nested
    @DisplayName("Derived state")
    inner class DerivedStateTests {

        @Test
        fun `canSubmit is false when email is blank`() {
            val state = AuthState(password = "pass")
            assertFalse(state.canSubmit)
        }

        @Test
        fun `canSubmit is false when password is empty`() {
            val state = AuthState(email = "a@b.com")
            assertFalse(state.canSubmit)
        }

        @Test
        fun `canSubmit is false when loading`() {
            val state = AuthState(email = "a@b.com", password = "pass", isLoading = true)
            assertFalse(state.canSubmit)
        }

        @Test
        fun `canSubmit is true when email and password filled and not loading`() {
            val state = AuthState(email = "a@b.com", password = "pass")
            assertTrue(state.canSubmit)
        }

        @Test
        fun `hasError reflects errorMessage presence`() {
            var state = AuthState()
            assertFalse(state.hasError)
            state = authReducer(state, AuthAction.LoginFailure(AppError.Unauthorized))
            assertTrue(state.hasError)
            state = authReducer(state, AuthAction.ErrorDismissed)
            assertFalse(state.hasError)
        }

        @Test
        fun `isLoggedIn is false by default`() {
            assertFalse(AuthState().isLoggedIn)
        }

        @Test
        fun `isLoggedIn is true after LoginSuccess`() {
            val result = authReducer(AuthState(), AuthAction.LoginSuccess(mockUser))
            assertTrue(result.isLoggedIn)
        }

        @Test
        fun `isLoggedIn is false after LogoutTapped`() {
            val state = AuthState(user = mockUser)
            assertTrue(state.isLoggedIn)
            val result = authReducer(state, AuthAction.LogoutTapped)
            assertFalse(result.isLoggedIn)
        }
    }

    // MARK: — State immutability

    @Nested
    @DisplayName("State immutability")
    inner class ImmutabilityTests {

        @Test
        fun `reducer returns new instance, does not mutate input`() {
            // FUSE: data class copy() guarantees immutability.
            // The original state is never modified.
            val original = AuthState(email = "original@test.com")
            val result = authReducer(original, AuthAction.EmailChanged("changed@test.com"))
            assertEquals("original@test.com", original.email)
            assertEquals("changed@test.com", result.email)
        }
    }
}
