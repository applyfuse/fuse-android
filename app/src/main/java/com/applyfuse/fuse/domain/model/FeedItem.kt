package com.applyfuse.fuse.domain.model

// FUSE: FeedItem is the domain model for a single post in the feed.
//
// Intentionally minimal — three fields. The point of the Feed
// feature is to demonstrate paginated state flow through the FUSE
// pattern, not to model a real social network. A host app that
// needs richer items (author, media, reactions, …) can map a
// richer type at the repository boundary; keeping the core model
// lean keeps the example approachable. Mirror of fuse-ios
// Sources/Models/FeedItem.swift.
//
// data class gives us equals()/hashCode() for state diffing in the
// reducer (FUSE rule 1: pure reducer, value comparison), copy()
// for test fixtures, and toString() for debugging.
//
// FUSE: NO @Serializable here. Android keeps wire types SEPARATE
// from domain models (the seam established in row 4: User/AuthTokens
// are pure domain; UserWire/LoginResponse are the @Serializable
// wire shapes living with the repository). FeedItem's @Serializable
// wire DTO (FeedItemWire) lands with LiveFeedRepository in row 10,
// NOT here — consistent with fuse-android's own convention over a
// line-by-line copy of iOS's Decodable-on-the-model approach. See
// DATA_LAYER.md decision log (Row 6).

data class FeedItem(
    val id: String,
    val title: String,
    val body: String
) {
    companion object {
        // FUSE: Preview/test item. Use FeedItem.mock wherever a
        // deterministic single item is needed (mirrors User.mock).
        val mock = FeedItem(
            id = "item_preview_001",
            title = "Example feed item",
            body = "Body content for the example item."
        )
    }
}
