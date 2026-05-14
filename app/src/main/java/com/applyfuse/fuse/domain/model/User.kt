package com.applyfuse.fuse.domain.model

// FUSE: User is the core domain model for authentication.
// It lives in domain/model/ — shared across features that
// need to know about the logged-in user.
//
// data class gives us:
// - equals() / hashCode() for state comparison in tests
// - copy() for creating modified instances
// - toString() for logging

data class User(
    val id: String,
    val email: String,
    val name: String
) {
    companion object {
        // FUSE: Preview/test user for unit tests and Compose previews.
        // Use User.mock wherever you need a deterministic User value.
        val mock = User(
            id = "usr_preview_001",
            email = "muhammad@applyfuse.com",
            name = "Muhammad"
        )
    }
}
