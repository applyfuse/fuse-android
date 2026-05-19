package com.applyfuse.fuse.domain.model

// FUSE: FeedPage is the page-based pagination shape.
//
// The reducer (row 7) combines successive pages into
// FeedState.items — APPEND on next-page load, REPLACE on refresh —
// and reads `hasMore` to decide whether to show the "load more"
// affordance. Mirror of fuse-ios Sources/Models/FeedPage.swift.
//
// Design notes (same as iOS):
// - `page` is 1-indexed. Matches the user-facing convention
//   ("page 3 of 12"), reads clearly in a debugger, and avoids the
//   off-by-one trap of asking for page 0.
// - `hasMore` is SERVER-AUTHORITATIVE, never derived from
//   items.size == expectedPageSize. The server may filter,
//   soft-delete, or apply logic the client must not
//   reverse-engineer. Trust the server's signal.
// - No `total` count. Adding one later is non-breaking (an extra
//   nullable field here + in the wire DTO).
//
// FUSE: NO @Serializable here — same seam as FeedItem / row 4.
// The @Serializable FeedPageWire lands with LiveFeedRepository in
// row 10. See DATA_LAYER.md decision log (Row 6).

data class FeedPage(
    val items: List<FeedItem>,
    val page: Int,
    val hasMore: Boolean
) {
    companion object {
        // FUSE: the canonical empty page — page 1, nothing loaded,
        // nothing more to load. Mirror of iOS FeedPage.empty;
        // FeedState's initial value builds on this.
        val empty = FeedPage(
            items = emptyList(),
            page = 1,
            hasMore = false
        )
    }
}
