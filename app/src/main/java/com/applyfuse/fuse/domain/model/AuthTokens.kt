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

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String
) {
    companion object {
        // FUSE: Preview/test tokens. Use AuthTokens.mock wherever a
        // deterministic token pair is needed in tests or previews.
        val mock = AuthTokens(
            accessToken = "access_preview_001",
            refreshToken = "refresh_preview_001"
        )
    }
}
