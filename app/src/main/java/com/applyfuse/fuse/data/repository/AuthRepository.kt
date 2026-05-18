package com.applyfuse.fuse.data.repository

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.domain.model.User
import kotlinx.coroutines.delay
import javax.inject.Inject

// FUSE: AuthRepository defines the contract between the feature
// layer and the data layer.

interface AuthRepository {
    suspend fun login(email: String, password: String): User
    suspend fun logout()
    suspend fun currentUser(): User?
}

// FUSE: Stub delays for Phase 1 — replaced with real Retrofit calls in Phase 2.
private const val STUB_LOGIN_DELAY_MS = 800L
private const val STUB_LOGOUT_DELAY_MS = 200L

class LiveAuthRepository @Inject constructor() : AuthRepository {

    override suspend fun login(email: String, password: String): User {
        // TODO Phase 2: replace with real Retrofit call
        delay(STUB_LOGIN_DELAY_MS)
        return User(id = "usr_live_001", email = email, name = "User")
    }

    override suspend fun logout() {
        // TODO Phase 2: invalidate token, clear EncryptedSharedPreferences
        delay(STUB_LOGOUT_DELAY_MS)
    }

    override suspend fun currentUser(): User? = null
}

class FakeAuthRepository(
    var shouldSucceed: Boolean = true,
    var mockUser: User = User.mock,
    var mockError: AppError = AppError.Unauthorized
) : AuthRepository {

    var loginCallCount = 0
    var logoutCallCount = 0
    var lastLoginEmail: String? = null

    override suspend fun login(email: String, password: String): User {
        loginCallCount++
        lastLoginEmail = email
        if (shouldSucceed) {
            return mockUser
        } else {
            throw mockError
        }
    }

    override suspend fun logout() {
        logoutCallCount++
        if (!shouldSucceed) {
            throw mockError
        }
    }

    override suspend fun currentUser(): User? =
        if (shouldSucceed) mockUser else null
}
