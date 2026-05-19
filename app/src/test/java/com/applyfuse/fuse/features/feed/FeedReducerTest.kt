package com.applyfuse.fuse.features.feed

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.domain.model.FeedItem
import com.applyfuse.fuse.domain.model.FeedPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

// FUSE: Reducer tests are the most valuable tests in the codebase.
// Zero mocks, zero coroutines, zero Android framework, zero UI.
// Arrange a state → call feedReducer(state, action) → assert the
// result. A failure here means the reducer logic is wrong; nothing
// else could have caused it. These tests pin all 7 invariants
// documented on feedReducer.

@DisplayName("FeedReducer")
class FeedReducerTest {

    private val itemA = FeedItem("a", "Title A", "Body A")
    private val itemB = FeedItem("b", "Title B", "Body B")
    private val itemC = FeedItem("c", "Title C", "Body C")

    private fun page(
        items: List<FeedItem>,
        page: Int = 1,
        hasMore: Boolean = false
    ) = FeedPage(items = items, page = page, hasMore = hasMore)

    // MARK: — LoadInitial

    @Nested
    @DisplayName("LoadInitial")
    inner class LoadInitialTests {

        @Test
        fun `from empty enters Initial loading`() {
            val result = feedReducer(FeedState(), FeedAction.LoadInitial)
            assertEquals(FeedLoadingState.Initial, result.loading)
        }

        @Test
        fun `clears any prior error at the start of the attempt`() {
            // FUSE: invariant 7 — error clears at the START of the
            // next load, not the end of the previous one.
            val state = FeedState(error = AppError.NetworkUnavailable)
            val result = feedReducer(state, FeedAction.LoadInitial)
            assertNull(result.error)
        }

        @Test
        fun `is a no-op when items already exist`() {
            // FUSE: invariant 3 — once populated, use Refresh.
            val state = FeedState(
                items = listOf(itemA),
                currentPage = 1
            )
            val result = feedReducer(state, FeedAction.LoadInitial)
            assertEquals(state, result)
            assertEquals(FeedLoadingState.Idle, result.loading)
        }

        @Test
        fun `does not touch items or page`() {
            val result = feedReducer(FeedState(), FeedAction.LoadInitial)
            assertTrue(result.items.isEmpty())
            assertEquals(FeedState.NOT_LOADED, result.currentPage)
        }
    }

    // MARK: — Refresh

    @Nested
    @DisplayName("Refresh")
    inner class RefreshTests {

        @Test
        fun `from idle enters Refreshing`() {
            val state = FeedState(items = listOf(itemA), currentPage = 1)
            val result = feedReducer(state, FeedAction.Refresh)
            assertEquals(FeedLoadingState.Refreshing, result.loading)
        }

        @Test
        fun `keeps existing items visible while refreshing`() {
            // FUSE: items stay until the replacement page arrives.
            val state = FeedState(items = listOf(itemA, itemB), currentPage = 1)
            val result = feedReducer(state, FeedAction.Refresh)
            assertEquals(listOf(itemA, itemB), result.items)
        }

        @Test
        fun `clears prior error`() {
            val state = FeedState(error = AppError.ServerError(500))
            val result = feedReducer(state, FeedAction.Refresh)
            assertNull(result.error)
        }

        @Test
        fun `is a no-op while Initial loading`() {
            val state = FeedState(loading = FeedLoadingState.Initial)
            val result = feedReducer(state, FeedAction.Refresh)
            assertEquals(state, result)
        }

        @Test
        fun `is a no-op while already Refreshing`() {
            val state = FeedState(loading = FeedLoadingState.Refreshing)
            val result = feedReducer(state, FeedAction.Refresh)
            assertEquals(state, result)
        }

        @Test
        fun `is a no-op while LoadingMore`() {
            val state = FeedState(loading = FeedLoadingState.LoadingMore)
            val result = feedReducer(state, FeedAction.Refresh)
            assertEquals(state, result)
        }
    }

    // MARK: — LoadMore

    @Nested
    @DisplayName("LoadMore")
    inner class LoadMoreTests {

        @Test
        fun `from idle with hasMore enters LoadingMore`() {
            val state = FeedState(
                items = listOf(itemA),
                currentPage = 1,
                hasMore = true
            )
            val result = feedReducer(state, FeedAction.LoadMore)
            assertEquals(FeedLoadingState.LoadingMore, result.loading)
        }

        @Test
        fun `is a no-op when hasMore is false`() {
            // FUSE: invariant 3 — server said there are no more pages.
            val state = FeedState(
                items = listOf(itemA),
                currentPage = 1,
                hasMore = false
            )
            val result = feedReducer(state, FeedAction.LoadMore)
            assertEquals(state, result)
        }

        @Test
        fun `is a no-op while Initial loading`() {
            val state = FeedState(hasMore = true, loading = FeedLoadingState.Initial)
            val result = feedReducer(state, FeedAction.LoadMore)
            assertEquals(state, result)
        }

        @Test
        fun `is a no-op while Refreshing`() {
            val state = FeedState(hasMore = true, loading = FeedLoadingState.Refreshing)
            val result = feedReducer(state, FeedAction.LoadMore)
            assertEquals(state, result)
        }

        @Test
        fun `is a no-op while already LoadingMore`() {
            val state = FeedState(hasMore = true, loading = FeedLoadingState.LoadingMore)
            val result = feedReducer(state, FeedAction.LoadMore)
            assertEquals(state, result)
        }

        @Test
        fun `clears prior error when it does proceed`() {
            val state = FeedState(
                items = listOf(itemA),
                currentPage = 1,
                hasMore = true,
                error = AppError.Timeout
            )
            val result = feedReducer(state, FeedAction.LoadMore)
            assertNull(result.error)
        }
    }

    // MARK: — FeedLoaded (replace: Initial / Refreshing)

    @Nested
    @DisplayName("FeedLoaded — replace semantics")
    inner class FeedLoadedReplaceTests {

        @Test
        fun `Initial load replaces items`() {
            // FUSE: invariant 4 — Initial → REPLACE.
            val state = FeedState(loading = FeedLoadingState.Initial)
            val result = feedReducer(
                state,
                FeedAction.FeedLoaded(page(listOf(itemA, itemB), page = 1, hasMore = true))
            )
            assertEquals(listOf(itemA, itemB), result.items)
        }

        @Test
        fun `Refreshing replaces items, discarding the old list`() {
            // FUSE: invariant 4 — Refreshing → REPLACE (not append).
            val state = FeedState(
                items = listOf(itemA, itemB),
                currentPage = 1,
                loading = FeedLoadingState.Refreshing
            )
            val result = feedReducer(
                state,
                FeedAction.FeedLoaded(page(listOf(itemC), page = 1, hasMore = false))
            )
            assertEquals(listOf(itemC), result.items)
        }

        @Test
        fun `updates currentPage and hasMore from the page`() {
            val state = FeedState(loading = FeedLoadingState.Initial)
            val result = feedReducer(
                state,
                FeedAction.FeedLoaded(page(listOf(itemA), page = 3, hasMore = true))
            )
            assertEquals(3, result.currentPage)
            assertTrue(result.hasMore)
        }

        @Test
        fun `returns to Idle and clears error`() {
            val state = FeedState(
                loading = FeedLoadingState.Initial,
                error = AppError.NetworkUnavailable
            )
            val result = feedReducer(
                state,
                FeedAction.FeedLoaded(page(listOf(itemA)))
            )
            assertEquals(FeedLoadingState.Idle, result.loading)
            assertNull(result.error)
        }

        @Test
        fun `hasMore false is honoured (server-authoritative)`() {
            // FUSE: hasMore comes from the server, never derived
            // from items.size.
            val state = FeedState(loading = FeedLoadingState.Initial)
            val result = feedReducer(
                state,
                FeedAction.FeedLoaded(page(listOf(itemA, itemB, itemC), page = 1, hasMore = false))
            )
            assertFalse(result.hasMore)
        }
    }

    // MARK: — FeedLoaded (append: LoadingMore)

    @Nested
    @DisplayName("FeedLoaded — append semantics")
    inner class FeedLoadedAppendTests {

        @Test
        fun `LoadingMore appends to existing items`() {
            // FUSE: invariant 4 — LoadingMore → APPEND.
            val state = FeedState(
                items = listOf(itemA, itemB),
                currentPage = 1,
                hasMore = true,
                loading = FeedLoadingState.LoadingMore
            )
            val result = feedReducer(
                state,
                FeedAction.FeedLoaded(page(listOf(itemC), page = 2, hasMore = false))
            )
            assertEquals(listOf(itemA, itemB, itemC), result.items)
        }

        @Test
        fun `append preserves order (old first, new after)`() {
            val state = FeedState(
                items = listOf(itemA),
                currentPage = 1,
                hasMore = true,
                loading = FeedLoadingState.LoadingMore
            )
            val result = feedReducer(
                state,
                FeedAction.FeedLoaded(page(listOf(itemB, itemC), page = 2, hasMore = true))
            )
            assertEquals(listOf(itemA, itemB, itemC), result.items)
        }

        @Test
        fun `advances currentPage to the loaded page`() {
            val state = FeedState(
                items = listOf(itemA),
                currentPage = 1,
                hasMore = true,
                loading = FeedLoadingState.LoadingMore
            )
            val result = feedReducer(
                state,
                FeedAction.FeedLoaded(page(listOf(itemB), page = 2, hasMore = true))
            )
            assertEquals(2, result.currentPage)
            assertTrue(result.hasMore)
        }

        @Test
        fun `returns to Idle after appending`() {
            val state = FeedState(
                items = listOf(itemA),
                currentPage = 1,
                hasMore = true,
                loading = FeedLoadingState.LoadingMore
            )
            val result = feedReducer(
                state,
                FeedAction.FeedLoaded(page(listOf(itemB), page = 2, hasMore = false))
            )
            assertEquals(FeedLoadingState.Idle, result.loading)
            assertFalse(result.hasMore)
        }
    }

    // MARK: — FeedLoaded (stale-response defense)

    @Nested
    @DisplayName("FeedLoaded — stale-response defense")
    inner class FeedLoadedStaleTests {

        @Test
        fun `dropped when loading is Idle`() {
            // FUSE: invariant 5 — a late page arriving after state
            // was reset is discarded; state is returned unchanged.
            val state = FeedState(
                items = listOf(itemA),
                currentPage = 1,
                hasMore = true,
                loading = FeedLoadingState.Idle
            )
            val result = feedReducer(
                state,
                FeedAction.FeedLoaded(page(listOf(itemB, itemC), page = 2, hasMore = false))
            )
            assertEquals(state, result)
        }

        @Test
        fun `stale drop does not alter items, page, or hasMore`() {
            val state = FeedState(
                items = listOf(itemA),
                currentPage = 1,
                hasMore = true,
                loading = FeedLoadingState.Idle
            )
            val result = feedReducer(
                state,
                FeedAction.FeedLoaded(page(emptyList(), page = 9, hasMore = false))
            )
            assertEquals(listOf(itemA), result.items)
            assertEquals(1, result.currentPage)
            assertTrue(result.hasMore)
        }
    }

    // MARK: — FeedFailed

    @Nested
    @DisplayName("FeedFailed")
    inner class FeedFailedTests {

        @Test
        fun `records the error and returns to Idle`() {
            val state = FeedState(loading = FeedLoadingState.Initial)
            val result = feedReducer(
                state,
                FeedAction.FeedFailed(AppError.NetworkUnavailable)
            )
            assertEquals(AppError.NetworkUnavailable, result.error)
            assertEquals(FeedLoadingState.Idle, result.loading)
        }

        @Test
        fun `a failed loadMore on page 2 does NOT empty the list`() {
            // FUSE: invariant 6 — failures never wipe items.
            val state = FeedState(
                items = listOf(itemA, itemB),
                currentPage = 2,
                hasMore = true,
                loading = FeedLoadingState.LoadingMore
            )
            val result = feedReducer(
                state,
                FeedAction.FeedFailed(AppError.Timeout)
            )
            assertEquals(listOf(itemA, itemB), result.items)
            assertEquals(2, result.currentPage)
            assertTrue(result.hasMore)
        }

        @Test
        fun `a failed initial load leaves the empty list empty`() {
            val state = FeedState(loading = FeedLoadingState.Initial)
            val result = feedReducer(
                state,
                FeedAction.FeedFailed(AppError.ServerError(500))
            )
            assertTrue(result.items.isEmpty())
            assertEquals(AppError.ServerError(500), result.error)
        }

        @Test
        fun `preserves the error type exactly (raw AppError, not stringified)`() {
            val state = FeedState(loading = FeedLoadingState.Refreshing)
            val result = feedReducer(
                state,
                FeedAction.FeedFailed(AppError.Forbidden)
            )
            assertNotNull(result.error)
            assertEquals(AppError.Forbidden, result.error)
        }
    }

    // MARK: — DismissError

    @Nested
    @DisplayName("DismissError")
    inner class DismissErrorTests {

        @Test
        fun `clears the error`() {
            val state = FeedState(error = AppError.Unauthorized)
            val result = feedReducer(state, FeedAction.DismissError)
            assertNull(result.error)
        }

        @Test
        fun `does not change items, page, hasMore, or loading`() {
            val state = FeedState(
                items = listOf(itemA, itemB),
                currentPage = 2,
                hasMore = true,
                loading = FeedLoadingState.Idle,
                error = AppError.Timeout
            )
            val result = feedReducer(state, FeedAction.DismissError)
            assertEquals(listOf(itemA, itemB), result.items)
            assertEquals(2, result.currentPage)
            assertTrue(result.hasMore)
            assertEquals(FeedLoadingState.Idle, result.loading)
        }
    }

    // MARK: — Derived state

    @Nested
    @DisplayName("Derived state")
    inner class DerivedStateTests {

        @Test
        fun `isEmpty true only when empty, idle, and no error`() {
            assertTrue(FeedState().isEmpty)
        }

        @Test
        fun `isEmpty false when items present`() {
            assertFalse(FeedState(items = listOf(itemA)).isEmpty)
        }

        @Test
        fun `isEmpty false while loading`() {
            assertFalse(FeedState(loading = FeedLoadingState.Initial).isEmpty)
        }

        @Test
        fun `isEmpty false when an error is present`() {
            assertFalse(FeedState(error = AppError.NetworkUnavailable).isEmpty)
        }

        @Test
        fun `canLoadMore true only when hasMore and idle`() {
            val state = FeedState(
                items = listOf(itemA),
                currentPage = 1,
                hasMore = true,
                loading = FeedLoadingState.Idle
            )
            assertTrue(state.canLoadMore)
        }

        @Test
        fun `canLoadMore false when hasMore is false`() {
            val state = FeedState(items = listOf(itemA), hasMore = false)
            assertFalse(state.canLoadMore)
        }

        @Test
        fun `canLoadMore false while loading even if hasMore`() {
            val state = FeedState(
                items = listOf(itemA),
                hasMore = true,
                loading = FeedLoadingState.LoadingMore
            )
            assertFalse(state.canLoadMore)
        }
    }

    // MARK: — Purity & immutability

    @Nested
    @DisplayName("Purity and immutability")
    inner class PurityTests {

        @Test
        fun `reducer returns a new instance, does not mutate input`() {
            // FUSE: invariant 1 — data class copy() guarantees the
            // input state is never modified.
            val original = FeedState(loading = FeedLoadingState.Initial)
            feedReducer(
                original,
                FeedAction.FeedLoaded(page(listOf(itemA), page = 1, hasMore = true))
            )
            assertEquals(FeedLoadingState.Initial, original.loading)
            assertTrue(original.items.isEmpty())
            assertEquals(FeedState.NOT_LOADED, original.currentPage)
        }

        @Test
        fun `same state and action always yield an equal result`() {
            // FUSE: invariant 1 — determinism.
            val state = FeedState(loading = FeedLoadingState.Initial)
            val action = FeedAction.FeedLoaded(page(listOf(itemA, itemB), page = 1, hasMore = true))
            assertEquals(feedReducer(state, action), feedReducer(state, action))
        }

        @Test
        fun `a no-op guard returns the very same instance`() {
            // FUSE: invariant 3 — no-ops return the input unchanged
            // (referential identity, not just equality).
            val state = FeedState(items = listOf(itemA), currentPage = 1)
            assertSame(state, feedReducer(state, FeedAction.LoadInitial))
        }
    }
}
