package com.applyfuse.fuse.features.feed

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.domain.model.FeedItem

// FUSE: FeedState is the single source of truth for the feed
// feature (FUSE rule 4). data class so every change produces a new
// copy via copy() — no accidental shared mutation. Mirror of
// fuse-ios Sources/Features/Feed/FeedState.swift.
//
// Pagination is page-based (locked decision, PHASE_2.md +
// DATA_LAYER.md). `currentPage` holds the last successfully-loaded
// page number, 1-indexed; `currentPage == 0` means "nothing loaded
// yet" (mirror of iOS).
//
// `loading` is a single enum, NOT a set of Boolean flags, so the
// reducer pattern-matches on it cleanly when feedLoaded arrives
// (replace items for Initial/Refreshing, append for LoadingMore)
// and impossible combinations are unrepresentable at compile time.
//
// FUSE: `error` holds the raw AppError? — NOT a pre-stringified
// String? like AuthState.errorMessage. This is a deliberate
// intra-repo shape difference, mirroring iOS FeedState.error:
// AppError? exactly, because the PHASE_2.md 7 invariants are
// specified against the raw error and row 8's ViewModel / row 9's
// screen derive the user-facing message at the edge. Recorded in
// DATA_LAYER.md decision log (Row 7).

data class FeedState(
    val items: List<FeedItem> = emptyList(),
    val currentPage: Int = NOT_LOADED,
    val hasMore: Boolean = false,
    val loading: FeedLoadingState = FeedLoadingState.Idle,
    val error: AppError? = null
) {
    // FUSE: empty AND not mid-load AND no error — the screen shows
    // a friendly "no posts yet" placeholder only here. && at
    // line-end per ChainWrapping (mirror of AuthState.canSubmit).
    val isEmpty: Boolean
        get() = items.isEmpty() &&
            loading == FeedLoadingState.Idle &&
            error == null

    // FUSE: user can request the next page only when the server
    // says more exist AND nothing is currently loading.
    val canLoadMore: Boolean
        get() = hasMore && loading == FeedLoadingState.Idle

    companion object {
        // FUSE: currentPage sentinel — 0 means "no page loaded
        // yet". Real pages are 1-indexed (locked decision), so 0
        // can never collide with a real page number.
        const val NOT_LOADED = 0

        val Empty = FeedState()

        val LoadingInitial = FeedState(
            loading = FeedLoadingState.Initial
        )

        val Loaded = FeedState(
            items = listOf(FeedItem.mock),
            currentPage = 1,
            hasMore = true
        )

        val Failed = FeedState(
            error = AppError.NetworkUnavailable
        )
    }
}

// FUSE: Four mutually-exclusive loading states. An enum, NOT three
// Booleans, so impossible states (e.g. "initial && loadingMore"
// simultaneously) are unrepresentable at compile time rather than
// asserted away. Mirror of iOS FeedLoadingState.
//
// Idle        — not loading; ready for the next user action
// Initial     — first ever load (no items yet); full-screen spinner
// Refreshing  — pull-to-refresh; items still visible, small spinner
// LoadingMore — paginating; items visible, footer spinner
enum class FeedLoadingState {
    Idle,
    Initial,
    Refreshing,
    LoadingMore
}
