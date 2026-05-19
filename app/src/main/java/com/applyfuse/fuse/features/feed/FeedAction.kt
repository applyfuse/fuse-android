package com.applyfuse.fuse.features.feed

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.domain.model.FeedPage

// FUSE: FeedAction is the complete vocabulary of the feed feature.
// Every possible thing that can happen — user intent or system
// response — is a case in this sealed class (FUSE rule: if it's not
// here, the reducer can't handle it). Mirror of fuse-ios
// Sources/Features/Feed/FeedAction.swift, using the in-repo
// AuthAction conventions (sealed class, data class for payload,
// object for no-data, // MARK: — split).
//
// Note: there is intentionally NO LoadInitialLoaded /
// RefreshLoaded / LoadMoreLoaded triplet. A single FeedLoaded
// carries the FeedPage, and the reducer reads state.loading to
// decide whether to REPLACE (Initial/Refreshing) or APPEND
// (LoadingMore). This keeps the action vocabulary small and
// demonstrates a strength of unidirectional state: response
// semantics flow from current state, not from request shape.

sealed class FeedAction {

    // MARK: — User intents
    // FUSE: Things the user explicitly does. Sent from the
    // Composable via the ViewModel.

    // FUSE: First load — typically dispatched when the feed screen
    // first appears. No-op in the reducer if items already exist
    // (use Refresh instead); the guard lives in the reducer.
    object LoadInitial : FeedAction()

    // FUSE: Pull-to-refresh. Replaces items on success. No-op if a
    // load is already in flight.
    object Refresh : FeedAction()

    // FUSE: User scrolled to the bottom / tapped "load more".
    // Appends the next page on success. No-op if already loading or
    // the server signalled no more pages (hasMore == false).
    object LoadMore : FeedAction()

    // FUSE: User dismissed the error toast/banner.
    object DismissError : FeedAction()

    // MARK: — System responses
    // FUSE: Results of async effects, dispatched by the ViewModel's
    // effect handler — never directly by the Composable.

    // FUSE: A page loaded successfully. The reducer reads
    // state.loading to decide replace vs append (see class note).
    data class FeedLoaded(val page: FeedPage) : FeedAction()

    // FUSE: A load failed. Carries the typed AppError; the reducer
    // records it and returns to Idle WITHOUT wiping existing items
    // (invariant 6).
    data class FeedFailed(val error: AppError) : FeedAction()
}
