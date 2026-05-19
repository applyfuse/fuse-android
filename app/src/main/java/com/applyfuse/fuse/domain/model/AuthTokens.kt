package com.applyfuse.fuse.domain.model

// FUSE: AuthTokens is the core domain model for the auth session.
// It lives in domain/model/ alongside User — the data layer
// (TokenStore) persists it; the network layer attaches the access
// token as a bearer and uses the refresh token to renew.
//
// data class gives us:
// - equals() / hashCode() for state comparison in tests
// - copy() for creating modified instances
// - toString() for logging (NOTE: avoid logging real tokens)
//
// FUSE: expiresAt mirrors fuse-ios AuthTokens.expiresAt (Date?).
// Epoch millis, NULLABLE: /auth/login supplies it (derived from the
// server's expiresIn seconds), but a token pair can legitimately
// exist without a known expiry (e.g. restored from older storage,
// or a server that omits it). Android's RefreshingHttpClient is
// REACTIVE (refreshes on a 401, not proactively on expiry) so this
// field is currently informational on Android — kept for
// cross-platform parity with iOS and to enable a future proactive
// refresh without a storage migration. See DATA_LAYER.md decision
// log (Row 4).

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long? = null
) {
    companion object {
        // FUSE: Preview/test tokens. Use AuthTokens.mock wherever a
        // deterministic token pair is needed in tests or previews.
        val mock = AuthTokens(
            accessToken = "access_preview_001",
            refreshToken = "refresh_preview_001",
            expiresAt = null
        )
    }
}
