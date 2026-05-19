package com.applyfuse.fuse.data.repository

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.data.network.HttpClient
import com.applyfuse.fuse.data.token.TokenStore
import com.applyfuse.fuse.domain.model.AuthTokens
import com.applyfuse.fuse.domain.model.User
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

// FUSE: AuthRepository defines the contract between the feature
// layer and the data layer. Unchanged from Phase 1 — the feature
// layer (AuthViewModel/reducer) and FakeAuthRepository depend on
// this exact shape, so Phase 2 only swaps the LiveAuthRepository
// BODY from stubs to real HttpClient + TokenStore coordination.

interface AuthRepository {
    suspend fun login(email: String, password: String): User
    suspend fun logout()
    suspend fun currentUser(): User?
}

// FUSE: LiveAuthRepository — the real implementation, a THIN
// coordination layer over HttpClient + TokenStore (mirror of
// fuse-ios LiveAuthRepository, PHASE_2 row 4 / iOS PR #10).
//
// Deliberately thin: translate a domain call into a request, hand
// the decoded response to the token store, return a domain model.
// NO retry / refresh logic here — that is RefreshingHttpClient's
// job (row 5). Keeping this layer thin keeps its tests focused on
// "did the right request go out and did we persist the right
// tokens", exactly as the iOS comment states.
//
// FUSE: @Inject takes the SHARED RefreshingHttpClient and the
// SHARED TokenStore (wired in row 10's Hilt module). The repo does
// not know or care that the client refreshes — it just calls it.
class LiveAuthRepository @Inject constructor(
    private val httpClient: HttpClient,
    private val tokenStore: TokenStore
) : AuthRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun login(email: String, password: String): User {
        val body = json.encodeToString(
            LoginRequest.serializer(),
            LoginRequest(email = email, password = password)
        )
        val response: LoginResponse = httpClient.post(PATH_LOGIN, body) { raw ->
            json.decodeFromString(LoginResponse.serializer(), raw)
        }
        tokenStore.write(response.toAuthTokens())
        return response.user.toDomain()
    }

    override suspend fun logout() {
        // FUSE: server logout is BEST-EFFORT (mirror of iOS). The
        // logout button must work offline / when the server is
        // down, so a failed POST is swallowed. A TokenStore.clear()
        // failure, however, is a real problem and DOES propagate —
        // the local session genuinely did not end.
        try {
            httpClient.post(PATH_LOGOUT, EMPTY_JSON) { }
        } catch (e: AppError) {
            // FUSE: intentionally swallowed — see above. Caught
            // narrowly (AppError, the only thing HttpClient throws)
            // not Throwable, so we never swallow CancellationException.
            Unit
        }
        tokenStore.clear()
    }

    override suspend fun currentUser(): User? {
        // FUSE: no tokens ⇒ not authenticated. Skip the network
        // entirely (a /me with no Authorization header would just
        // 401). Mirror of iOS's guard.
        if (tokenStore.read() == null) {
            return null
        }
        return httpClient.get(PATH_ME) { raw ->
            json.decodeFromString(UserWire.serializer(), raw)
        }.toDomain()
    }

    private companion object {
        const val PATH_LOGIN = "/auth/login"
        const val PATH_LOGOUT = "/auth/logout"
        const val PATH_ME = "/me"
        const val EMPTY_JSON = "{}"
    }
}

// FUSE: Wire types — internal, NOT in domain/model/, NOT public.
// The wire format is allowed to differ from the domain shape;
// keeping the seam here (mirror of iOS's internal wire structs)
// makes a future server change (renamed field, added envelope) a
// surgical edit in ONE place. internal (not private) so tests in
// the same module can build fixtures.
//
// Flat shape mirrors fuse-ios LoginResponse exactly (Decision 2,
// user-confirmed): NOT a nested { user, tokens:{} } envelope.
@Serializable
internal data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
internal data class UserWire(
    val id: String,
    val email: String,
    val name: String
) {
    fun toDomain(): User = User(id = id, email = email, name = name)
}

@Serializable
internal data class LoginResponse(
    val user: UserWire,
    val accessToken: String,
    val refreshToken: String,
    // FUSE: seconds-to-live from the server (mirror iOS expiresIn).
    // Converted to an absolute epoch-millis expiresAt at decode
    // time so the rest of the app deals in absolute time, not a
    // relative TTL whose meaning drifts the longer it is held.
    val expiresIn: Long? = null
) {
    fun toAuthTokens(nowMillis: Long = System.currentTimeMillis()): AuthTokens =
        AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresIn?.let { nowMillis + it * MILLIS_PER_SECOND }
        )

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}

// FUSE: Refresh-endpoint wire shapes. Separate from LoginResponse
// because /auth/refresh rotates tokens but does NOT return a fresh
// User (mirror of iOS's distinct RefreshResponse). Used by the
// refresh lambda wired in row 10's Hilt module — kept here next to
// the other auth wire types so the whole auth wire contract lives
// in one file.
@Serializable
internal data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
internal data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long? = null
) {
    fun toAuthTokens(nowMillis: Long = System.currentTimeMillis()): AuthTokens =
        AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresIn?.let { nowMillis + it * MILLIS_PER_SECOND }
        )

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}

// FUSE: FakeAuthRepository — UNCHANGED from Phase 1. The feature
// layer's tests (AuthViewModelTest) depend on this exact shape and
// behaviour; Phase 2 must not perturb it. It implements the same
// AuthRepository interface and never touches the network or a
// token store, so it stays a pure in-memory test double.
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
