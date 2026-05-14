package com.applyfuse.fuse.data.repository

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.domain.model.User
import kotlinx.coroutines.delay
import javax.inject.Inject

// FUSE: AuthRepository defines the contract between the feature
// layer and the data layer. It is an interface so the feature
// layer never depends on a concrete implementation.
//
// Rules:
// - Interface lives here, implementations are in the same file
// - All methods are suspend — callers handle AppError
// - No Android framework imports in the interface — pure Kotlin
// - Hilt binds the interface to LiveAuthRepository via the module

interface AuthRepository {

    // FUSE: Attempt login with email and password.
    // Returns the authenticated User on success.
    // Throws AppError on failure — never returns null.
    suspend fun login(email: String, password: String): User

    // FUSE: Sign out the current user and clear stored tokens.
    suspend fun logout()

    // FUSE: Return the currently authenticated User,
    // or null if no valid session exists.
    suspend fun currentUser(): User?
}

// MARK: - Live Implementation

// FUSE: LiveAuthRepository makes real network calls.
// It is the only implementation used in production.
// All exceptions are mapped to AppError before being thrown.
// @Inject tells Hilt how to construct this class.

class LiveAuthRepository @Inject constructor() : AuthRepository {

    override suspend fun login(email: String, password: String): User {
        // TODO Phase 2: replace with real Retrofit call
        // Simulate network latency for now
        delay(800)

        // FUSE: Map HTTP errors to AppError here, never in the reducer.
        // Example for when real network is wired:
        // if (!response.isSuccessful) {
        //     throw when (response.code()) {
        //         401  -> AppError.Unauthorized
        //         in 500..599 -> AppError.ServerError(response.code())
        //         else -> AppError.ClientError(response.code())
        //     }
        // }

        return User(id = "usr_live_001", email = email, name = "User")
    }

    override suspend fun logout() {
        // TODO Phase 2: invalidate token, clear EncryptedSharedPreferences
        delay(200)
    }

    override suspend fun currentUser(): User? {
        // TODO Phase 2: read token from EncryptedSharedPreferences, validate
        return null
    }
}

// MARK: - Fake Implementation

// FUSE: FakeAuthRepository is used in:
// - JUnit 5 unit tests (fast, deterministic, no network)
// - Compose @Preview (instant, no Hilt needed)
//
// shouldSucceed lets you test both happy and error paths
// without changing any other code.

class FakeAuthRepository(
    // FUSE: Toggle this to test error states in tests and previews.
    var shouldSucceed: Boolean = true,
    var mockUser: User = User.mock,
    var mockError: AppError = AppError.Unauthorized
) : AuthRepository {

    // FUSE: Track calls for assertion in tests.
    var loginCallCount = 0
    var logoutCallCount = 0
    var lastLoginEmail: String? = null

    override suspend fun login(email: String, password: String): User {
        loginCallCount++
        lastLoginEmail = email
        // FUSE: No real delay in fake — tests run instantly.
        if (shouldSucceed) return mockUser
        else throw mockError
    }

    override suspend fun logout() {
        logoutCallCount++
        if (!shouldSucceed) throw mockError
    }

    override suspend fun currentUser(): User? {
        return if (shouldSucceed) mockUser else null
    }
}
