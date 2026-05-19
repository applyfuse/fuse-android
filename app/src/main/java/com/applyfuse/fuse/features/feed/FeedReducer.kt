package com.applyfuse.fuse.features.feed

import com.applyfuse.fuse.domain.model.FeedPage

// FUSE: feedReducer is the pure-function heart of the feed feature
// (FUSE rule 1). Mirror of fuse-ios
// Sources/Features/Feed/FeedReducer.swift. Same signature shape as
// the in-repo authReducer: a top-level fun, single exhaustive
// `when (action)`, no `else`, returns a new FeedState.
//
// Invariants (all 7 — identical to iOS):
//
// 1. Pure. No coroutines, no IO, no side effects. Same
//    (state, action) in → same state out.
//
// 2. Total. Every FeedAction case handled exhaustively (sealed
//    class + no `else` → a new action is a COMPILE error here
//    until handled).
//
// 3. Conservative on guard violations. Actions inappropriate to
//    the current state are no-ops (return state unchanged):
//      - LoadInitial when items already exist → no-op (use Refresh)
//      - LoadMore when hasMore is false       → no-op
//      - LoadMore / Refresh while loading     → no-op (no
//        concurrent-loads policy — simplest model for teaching)
//
// 4. Loading state drives FeedLoaded semantics:
//      Initial     → REPLACE items
//      Refreshing  → REPLACE items
//      LoadingMore → APPEND items
//      Idle        → DROP payload (stale-response defense, see 5)
//
// 5. Stale-response defense. FeedLoaded arriving while loading is
//    Idle drops the payload — handles a slow request returning
//    after state was reset. The ViewModel's cancellation minimises
//    this; the reducer is defensive in depth.
//
// 6. Failures don't wipe items. FeedFailed records the error and
//    returns to Idle, but leaves items / currentPage / hasMore
//    untouched. A failed LoadMore on page 2 must NOT empty the
//    list.
//
// 7. Errors clear at the START of the next load attempt, not at
//    the end of the previous one. UI shows the error until the
//    user retries or dismisses.
//
// Structure note: FeedLoaded handling is extracted into
// applyFeedLoaded(state, page). detekt's CyclomaticComplexMethod
// caps method complexity; the inline version (6 outer cases +
// nested when + guards) trips it. Splitting along the natural seam
// — outer dispatch vs the loading-state-driven replace/append/drop
// decision — gives each function its own budget and reads cleaner.
// Behaviour is identical; this is a structural split only, exactly
// as iOS did it for the same SwiftLint rule (PHASE_2.md predicted
// this and prescribed the same fix).

fun feedReducer(state: FeedState, action: FeedAction): FeedState =
    when (action) {
        // FUSE: First load. No-op if items already exist — the
        // user should Refresh, not LoadInitial, once populated.
        // Clears any prior error (invariant 7: clear at the START
        // of the next attempt).
        is FeedAction.LoadInitial ->
            if (state.items.isNotEmpty()) {
                state
            } else {
                state.copy(
                    loading = FeedLoadingState.Initial,
                    error = null
                )
            }

        // FUSE: Pull-to-refresh. No-op if a load is already in
        // flight (no concurrent loads). Items stay visible until
        // the replacement page arrives.
        is FeedAction.Refresh ->
            if (state.loading != FeedLoadingState.Idle) {
                state
            } else {
                state.copy(
                    loading = FeedLoadingState.Refreshing,
                    error = null
                )
            }

        // FUSE: Next page. No-op if already loading OR the server
        // said there are no more pages (hasMore == false).
        is FeedAction.LoadMore ->
            if (state.loading != FeedLoadingState.Idle || !state.hasMore) {
                state
            } else {
                state.copy(
                    loading = FeedLoadingState.LoadingMore,
                    error = null
                )
            }

        // FUSE: Success — delegate to the helper, which reads
        // state.loading to decide replace vs append vs drop.
        is FeedAction.FeedLoaded ->
            applyFeedLoaded(state, action.page)

        // FUSE: Failure — record the error, return to Idle, leave
        // items/currentPage/hasMore UNTOUCHED (invariant 6).
        is FeedAction.FeedFailed ->
            state.copy(
                loading = FeedLoadingState.Idle,
                error = action.error
            )

        // FUSE: Dismiss the error banner; nothing else changes.
        is FeedAction.DismissError ->
            state.copy(error = null)
    }

// FUSE: Private helper for FeedLoaded. Decides whether the incoming
// page REPLACES or APPENDS items based on the current loading
// state, then updates currentPage / hasMore / loading / error.
//
// Returning the input state UNCHANGED when loading is Idle is the
// stale-response defense (invariant 5).
private fun applyFeedLoaded(state: FeedState, page: FeedPage): FeedState =
    when (state.loading) {
        FeedLoadingState.Initial,
        FeedLoadingState.Refreshing ->
            state.copy(
                items = page.items,
                currentPage = page.page,
                hasMore = page.hasMore,
                loading = FeedLoadingState.Idle,
                error = null
            )

        FeedLoadingState.LoadingMore ->
            state.copy(
                items = state.items + page.items,
                currentPage = page.page,
                hasMore = page.hasMore,
                loading = FeedLoadingState.Idle,
                error = null
            )

        // FUSE: Stale response — loading was Idle, so this page is
        // the late result of a request whose state was already
        // reset. Drop it (invariant 5): return state unchanged.
        FeedLoadingState.Idle ->
            state
    }
