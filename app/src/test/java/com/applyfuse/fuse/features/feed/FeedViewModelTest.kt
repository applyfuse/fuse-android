package com.applyfuse.fuse.features.feed

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.data.repository.FakeFeedRepository
import com.applyfuse.fuse.domain.model.FeedItem
import com.applyfuse.fuse.domain.model.FeedPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

// FUSE: FeedViewModel integration tests. Constructs FeedViewModel
// directly with FakeFeedRepository — no Hilt, no DI, no shim (the
// VM is standalone, not a BaseViewModel subclass, so unlike
// AuthViewModelTest there is no Test*ViewModel needed).
//
// Same coroutine-test harness as AuthViewModelTest (the in-repo
// convention the gate already accepts): UnconfinedTestDispatcher +
// Dispatchers.setMain/resetMain, runTest + advanceUntilIdle().
//
// The most important behaviour pinned here is the previousLoading
// guard — the entire reason FeedViewModel is standalone (Option C).
// Tests assert no-op actions do NOT issue a network request and
// response actions do NOT recurse, via FakeFeedRepository's
// loadFeedCalls tracking.

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("FeedViewModel integration")
class FeedViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val itemA = FeedItem("a", "Title A", "Body A")
    private val itemB = FeedItem("b", "Title B", "Body B")
    private val itemC = FeedItem("c", "Title C", "Body C")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty`() {
        val vm = FeedViewModel(FakeFeedRepository())
        assertEquals(FeedState(), vm.state.value)
        assertTrue(vm.state.value.isEmpty)
        assertEquals(FeedLoadingState.Idle, vm.state.value.loading)
    }

    @Nested
    @DisplayName("LoadInitial")
    inner class LoadInitialTests {

        @Test
        fun `loads page 1 and populates items`() = runTest {
            val fake = FakeFeedRepository(
                stubbedPages = mapOf(
                    1 to FeedPage(listOf(itemA, itemB), page = 1, hasMore = true)
                )
            )
            val vm = FeedViewModel(fake)
            vm.send(FeedAction.LoadInitial)
            advanceUntilIdle()
            assertEquals(listOf(itemA, itemB), vm.state.value.items)
            assertEquals(1, vm.state.value.currentPage)
            assertTrue(vm.state.value.hasMore)
            assertEquals(FeedLoadingState.Idle, vm.state.value.loading)
        }

        @Test
        fun `requests exactly page 1 once`() = runTest {
            val fake = FakeFeedRepository(
                stubbedPages = mapOf(1 to FeedPage(listOf(itemA), page = 1, hasMore = false))
            )
            val vm = FeedViewModel(fake)
            vm.send(FeedAction.LoadInitial)
            advanceUntilIdle()
            assertEquals(listOf(1), fake.loadFeedCalls)
        }

        @Test
        fun `is a no-op when items already exist — no network call`() = runTest {
            // FUSE: the previousLoading guard in action. Reducer
            // no-ops LoadInitial when items exist (loading stays
            // Idle, unchanged) → send() must NOT fire an effect.
            val fake = FakeFeedRepository(
                stubbedPages = mapOf(1 to FeedPage(listOf(itemA), page = 1, hasMore = true))
            )
            val vm = FeedViewModel(fake)
            vm.send(FeedAction.LoadInitial)
            advanceUntilIdle()
            assertEquals(listOf(1), fake.loadFeedCalls)

            vm.send(FeedAction.LoadInitial)
            advanceUntilIdle()
            // FUSE: still exactly one call — the second LoadInitial
            // no-op'd and fired no effect.
            assertEquals(listOf(1), fake.loadFeedCalls)
        }
    }

    @Nested
    @DisplayName("Refresh")
    inner class RefreshTests {

        @Test
        fun `replaces items with the fresh page`() = runTest {
            val fake = FakeFeedRepository(
                stubbedPages = mapOf(1 to FeedPage(listOf(itemA), page = 1, hasMore = true))
            )
            val vm = FeedViewModel(fake)
            vm.send(FeedAction.LoadInitial)
            advanceUntilIdle()

            fake.stubbedPages = mapOf(1 to FeedPage(listOf(itemC), page = 1, hasMore = false))
            vm.send(FeedAction.Refresh)
            advanceUntilIdle()
            assertEquals(listOf(itemC), vm.state.value.items)
            assertFalse(vm.state.value.hasMore)
        }

        @Test
        fun `requests page 1 on refresh`() = runTest {
            val fake = FakeFeedRepository(
                stubbedPages = mapOf(1 to FeedPage(listOf(itemA), page = 1, hasMore = true))
            )
            val vm = FeedViewModel(fake)
            vm.send(FeedAction.LoadInitial)
            advanceUntilIdle()
            vm.send(FeedAction.Refresh)
            advanceUntilIdle()
            // FUSE: page 1 requested twice — once initial, once refresh.
            assertEquals(listOf(1, 1), fake.loadFeedCalls)
        }
    }

    @Nested
    @DisplayName("LoadMore — pagination")
    inner class LoadMoreTests {

        @Test
        fun `appends the next page and advances currentPage`() = runTest {
            val fake = FakeFeedRepository(
                stubbedPages = mapOf(
                    1 to FeedPage(listOf(itemA, itemB), page = 1, hasMore = true),
                    2 to FeedPage(listOf(itemC), page = 2, hasMore = false)
                )
            )
            val vm = FeedViewModel(fake)
            vm.send(FeedAction.LoadInitial)
            advanceUntilIdle()
            vm.send(FeedAction.LoadMore)
            advanceUntilIdle()
            assertEquals(listOf(itemA, itemB, itemC), vm.state.value.items)
            assertEquals(2, vm.state.value.currentPage)
            assertFalse(vm.state.value.hasMore)
        }

        @Test
        fun `requests currentPage + 1`() = runTest {
            // FUSE: pinning the iOS pagination contract — LoadMore
            // asks for currentPage+1 (the last loaded page + 1).
            val fake = FakeFeedRepository(
                stubbedPages = mapOf(
                    1 to FeedPage(listOf(itemA), page = 1, hasMore = true),
                    2 to FeedPage(listOf(itemB), page = 2, hasMore = true),
                    3 to FeedPage(listOf(itemC), page = 3, hasMore = false)
                )
            )
            val vm = FeedViewModel(fake)
            vm.send(FeedAction.LoadInitial)
            advanceUntilIdle()
            vm.send(FeedAction.LoadMore)
            advanceUntilIdle()
            vm.send(FeedAction.LoadMore)
            advanceUntilIdle()
            assertEquals(listOf(1, 2, 3), fake.loadFeedCalls)
        }

        @Test
        fun `is a no-op when hasMore is false — no network call`() = runTest {
            // FUSE: previousLoading guard — reducer no-ops LoadMore
            // when hasMore=false (loading stays Idle) → no effect.
            val fake = FakeFeedRepository(
                stubbedPages = mapOf(1 to FeedPage(listOf(itemA), page = 1, hasMore = false))
            )
            val vm = FeedViewModel(fake)
            vm.send(FeedAction.LoadInitial)
            advanceUntilIdle()
            assertEquals(listOf(1), fake.loadFeedCalls)

            vm.send(FeedAction.LoadMore)
            advanceUntilIdle()
            // FUSE: no page-2 request — hasMore was false.
            assertEquals(listOf(1), fake.loadFeedCalls)
        }
    }

    @Nested
    @DisplayName("Failure path")
    inner class FailureTests {

        @Test
        fun `records the error and stays at Idle`() = runTest {
            val fake = FakeFeedRepository(errorToThrow = AppError.NetworkUnavailable)
            val vm = FeedViewModel(fake)
            vm.send(FeedAction.LoadInitial)
            advanceUntilIdle()
            assertEquals(AppError.NetworkUnavailable, vm.state.value.error)
            assertEquals(FeedLoadingState.Idle, vm.state.value.loading)
        }

        @Test
        fun `a failed loadMore does not wipe existing items`() = runTest {
            // FUSE: invariant 6 end-to-end through the VM.
            val fake = FakeFeedRepository(
                stubbedPages = mapOf(1 to FeedPage(listOf(itemA, itemB), page = 1, hasMore = true))
            )
            val vm = FeedViewModel(fake)
            vm.send(FeedAction.LoadInitial)
            advanceUntilIdle()

            fake.errorToThrow = AppError.Timeout
            vm.send(FeedAction.LoadMore)
            advanceUntilIdle()
            assertEquals(listOf(itemA, itemB), vm.state.value.items)
            assertEquals(AppError.Timeout, vm.state.value.error)
            assertEquals(1, vm.state.value.currentPage)
        }

        @Test
        fun `retry after failure succeeds and clears error`() = runTest {
            val fake = FakeFeedRepository(errorToThrow = AppError.ServerError(500))
            val vm = FeedViewModel(fake)
            vm.send(FeedAction.LoadInitial)
            advanceUntilIdle()
            assertNotNull(vm.state.value.error)

            fake.errorToThrow = null
            fake.stubbedPages = mapOf(1 to FeedPage(listOf(itemA), page = 1, hasMore = false))
            vm.send(FeedAction.Refresh)
            advanceUntilIdle()
            assertNull(vm.state.value.error)
            assertEquals(listOf(itemA), vm.state.value.items)
        }
    }

    @Nested
    @DisplayName("Effect-firing guard (previousLoading)")
    inner class EffectGuardTests {

        @Test
        fun `DismissError does not trigger a network call`() = runTest {
            // FUSE: DismissError leaves loading == Idle, so the
            // guard's `newLoading != Idle` arm filters it — no
            // effect, no recursion.
            val fake = FakeFeedRepository(errorToThrow = AppError.Unauthorized)
            val vm = FeedViewModel(fake)
            vm.send(FeedAction.LoadInitial)
            advanceUntilIdle()
            assertEquals(listOf(1), fake.loadFeedCalls)

            vm.send(FeedAction.DismissError)
            advanceUntilIdle()
            assertNull(vm.state.value.error)
            // FUSE: no extra request from DismissError.
            assertEquals(listOf(1), fake.loadFeedCalls)
        }

        @Test
        fun `response actions do not recurse into more network calls`() = runTest {
            // FUSE: the core anti-recursion property. One
            // LoadInitial → exactly one loadFeed; the resulting
            // FeedLoaded send() must NOT itself spawn another
            // effect (it returns to Idle, guard filters it).
            val fake = FakeFeedRepository(
                stubbedPages = mapOf(1 to FeedPage(listOf(itemA), page = 1, hasMore = true))
            )
            val vm = FeedViewModel(fake)
            vm.send(FeedAction.LoadInitial)
            advanceUntilIdle()
            assertEquals(1, fake.loadFeedCalls.size)
            assertEquals(listOf(1), fake.loadFeedCalls)
        }

        @Test
        fun `LoadMore while a load is in flight does not double-fire`() = runTest {
            // FUSE: the precise bug Option C exists to prevent. Two
            // LoadMore in a row: the first transitions to
            // LoadingMore (fires); the second, before the first
            // completes, finds loading != Idle so the reducer
            // no-ops it (loading unchanged) → previousLoading ==
            // newLoading → guard filters it → NO second request for
            // the same page. With FakeFeedRepository's synchronous
            // suspend, advanceUntilIdle drains the first fully, so
            // we assert the stronger end-state: no duplicate page
            // in loadFeedCalls.
            val fake = FakeFeedRepository(
                stubbedPages = mapOf(
                    1 to FeedPage(listOf(itemA), page = 1, hasMore = true),
                    2 to FeedPage(listOf(itemB), page = 2, hasMore = true)
                )
            )
            val vm = FeedViewModel(fake)
            vm.send(FeedAction.LoadInitial)
            advanceUntilIdle()
            vm.send(FeedAction.LoadMore)
            vm.send(FeedAction.LoadMore)
            advanceUntilIdle()
            // FUSE: page 2 requested exactly once despite two
            // LoadMore sends — no [1,2,2] or [1,2,3].
            assertEquals(listOf(1, 2), fake.loadFeedCalls)
        }
    }
}
