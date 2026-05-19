package com.applyfuse.fuse.data.repository

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.domain.model.FeedPage

// FUSE: FeedRepository defines the data contract for the feed
// feature. Mirrors AuthRepository's shape — a small, suspend-only
// surface that hides transport, retry, and refresh concerns from
// the reducer and ViewModel. Mirror of fuse-ios
// FeedRepositoryProtocol.
//
// NOTE: this file currently holds the interface + FakeFeedRepository
// ONLY. LiveFeedRepository (real HttpClient-backed impl + its
// @Serializable wire DTOs + Hilt binding + ~13 repo tests) lands in
// PHASE_2 row 10. This mirrors how AuthRepository existed in Phase 1
// as interface + Fake, with row 4 only swapping in the Live body —
// the interface/fake preceding the live impl is the established repo
// pattern here, NOT a deviation. Row 8's FeedViewModel needs a
// FeedRepository type to inject and a fake to test against; those
// are the minimal contract, so they land here. See DATA_LAYER.md
// decision log (Row 8).

interface FeedRepository {
    suspend fun loadFeed(page: Int): FeedPage
}

// FUSE: FakeFeedRepository — the test double for FeedViewModelTest
// and the preview/default feed repository. Mirror of iOS
// MockFeedRepository, using the in-repo FakeAuthRepository
// conventions (plain class, public mutable knobs, call-tracking
// with a private-set list).
//
// Tests script per-page responses via `stubbedPages`, or set
// `errorToThrow` for failure paths. `loadFeedCalls` records exactly
// what the ViewModel asked for, so tests can assert pagination
// behaviour ("did it request page 2 after the user scrolled?").
//
// Reducer tests do NOT use this — the reducer is pure and receives
// FeedPage directly via FeedAction.FeedLoaded. This fake exists
// only for the ViewModel layer (row 8) and as a preview default.
class FakeFeedRepository(
    var stubbedPages: Map<Int, FeedPage> = emptyMap(),
    var errorToThrow: AppError? = null
) : FeedRepository {

    var loadFeedCalls: List<Int> = emptyList()
        private set

    override suspend fun loadFeed(page: Int): FeedPage {
        loadFeedCalls = loadFeedCalls + page
        errorToThrow?.let { throw it }
        return stubbedPages[page] ?: FeedPage(
            items = emptyList(),
            page = page,
            hasMore = false
        )
    }
}
