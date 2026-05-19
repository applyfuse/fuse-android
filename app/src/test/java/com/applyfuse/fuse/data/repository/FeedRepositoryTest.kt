package com.applyfuse.fuse.data.repository

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.data.network.FakeHttpClient
import com.applyfuse.fuse.domain.model.FeedItem
import com.applyfuse.fuse.domain.model.FeedPage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

// FUSE: LiveFeedRepository is a THIN coordination layer. These
// tests assert exactly that: the right request went out
// (path + ?page=N) and the wire shape was correctly mapped to the
// domain shape (has_more -> hasMore, FeedItemWire -> FeedItem) —
// never network behaviour (that's LiveHttpClientTest) and never
// refresh (that's RefreshingHttpClientTest, inherited via the
// shared client). FakeHttpClient, no mocks, no TokenStore (feed
// never writes tokens) — mirror of LiveAuthRepositoryTest's focus
// and fuse-docs ARCHITECTURE.md §8 ("Repository — interface
// contract — mock network responses").
@DisplayName("LiveFeedRepository")
class FeedRepositoryTest {

    private lateinit var http: FakeHttpClient
    private lateinit var repo: LiveFeedRepository

    @BeforeEach
    fun setUp() {
        http = FakeHttpClient()
        repo = LiveFeedRepository(http)
    }

    @Nested
    @DisplayName("request construction")
    inner class RequestTests {

        @Test
        fun `gets feed with the page query parameter`() = runTest {
            http.nextResponse = """{"items":[],"page":1,"has_more":false}"""

            repo.loadFeed(1)

            assertEquals("/feed?page=1", http.lastPath)
            assertEquals(1, http.getCallCount)
        }

        @Test
        fun `sends the exact page number requested`() = runTest {
            http.nextResponse = """{"items":[],"page":7,"has_more":false}"""

            repo.loadFeed(7)

            assertEquals("/feed?page=7", http.lastPath)
        }

        @Test
        fun `uses GET, never POST`() = runTest {
            http.nextResponse = """{"items":[],"page":1,"has_more":false}"""

            repo.loadFeed(1)

            assertEquals(1, http.getCallCount)
            assertEquals(0, http.postCallCount)
        }
    }

    @Nested
    @DisplayName("wire to domain mapping")
    inner class MappingTests {

        @Test
        fun `maps a populated page to the domain model`() = runTest {
            http.nextResponse = """
                {"items":[
                  {"id":"1","title":"First","body":"Body one"},
                  {"id":"2","title":"Second","body":"Body two"}
                ],"page":1,"has_more":true}
            """.trimIndent()

            val page = repo.loadFeed(1)

            assertEquals(
                FeedPage(
                    items = listOf(
                        FeedItem("1", "First", "Body one"),
                        FeedItem("2", "Second", "Body two")
                    ),
                    page = 1,
                    hasMore = true
                ),
                page
            )
        }

        @Test
        fun `maps snake_case has_more to hasMore (true)`() = runTest {
            http.nextResponse = """{"items":[],"page":1,"has_more":true}"""

            val page = repo.loadFeed(1)

            assertTrue(page.hasMore)
        }

        @Test
        fun `maps snake_case has_more to hasMore (false)`() = runTest {
            http.nextResponse = """{"items":[],"page":4,"has_more":false}"""

            val page = repo.loadFeed(4)

            assertFalse(page.hasMore)
        }

        @Test
        fun `preserves the server page number`() = runTest {
            http.nextResponse = """{"items":[],"page":3,"has_more":true}"""

            val page = repo.loadFeed(3)

            assertEquals(3, page.page)
        }

        @Test
        fun `maps each item field id title body exactly`() = runTest {
            http.nextResponse = """
                {"items":[{"id":"abc","title":"T","body":"B"}],
                 "page":1,"has_more":false}
            """.trimIndent()

            val item = repo.loadFeed(1).items.single()

            assertEquals("abc", item.id)
            assertEquals("T", item.title)
            assertEquals("B", item.body)
        }

        @Test
        fun `an empty items array yields an empty domain list`() = runTest {
            http.nextResponse = """{"items":[],"page":1,"has_more":false}"""

            val page = repo.loadFeed(1)

            assertTrue(page.items.isEmpty())
        }

        @Test
        fun `ignores unknown server fields (forward-compatible wire)`() = runTest {
            // FUSE: Json { ignoreUnknownKeys = true } — a server
            // adding a field must never break decoding.
            http.nextResponse = """
                {"items":[{"id":"1","title":"T","body":"B","extra":"ignored"}],
                 "page":1,"has_more":false,"server_time":"2026-05-19"}
            """.trimIndent()

            val page = repo.loadFeed(1)

            assertEquals(FeedItem("1", "T", "B"), page.items.single())
        }
    }

    @Nested
    @DisplayName("error propagation")
    inner class ErrorTests {

        @Test
        fun `propagates the AppError the transport throws`() = runTest {
            http.errorToThrow = AppError.ServerError(500)

            var thrown: Throwable? = null
            try {
                repo.loadFeed(1)
            } catch (e: AppError) {
                thrown = e
            }

            assertTrue(thrown is AppError.ServerError)
            assertEquals(500, (thrown as AppError.ServerError).statusCode)
        }

        @Test
        fun `propagates Unauthorized unchanged (refresh is the client's job)`() = runTest {
            // FUSE: the repo does NOT handle 401 — the shared
            // RefreshingHttpClient does, transparently. If it
            // ultimately fails, Unauthorized reaches the repo and
            // must pass through untouched.
            http.errorToThrow = AppError.Unauthorized

            var thrown: Throwable? = null
            try {
                repo.loadFeed(2)
            } catch (e: AppError) {
                thrown = e
            }

            assertTrue(thrown is AppError.Unauthorized)
        }

        @Test
        fun `does not swallow or wrap a NetworkUnavailable`() = runTest {
            http.errorToThrow = AppError.NetworkUnavailable

            var thrown: Throwable? = null
            try {
                repo.loadFeed(1)
            } catch (e: AppError) {
                thrown = e
            }

            assertTrue(thrown is AppError.NetworkUnavailable)
        }
    }
}
