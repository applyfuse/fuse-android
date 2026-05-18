package com.applyfuse.fuse.data.network

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.data.token.InMemoryTokenStore
import com.applyfuse.fuse.domain.model.AuthTokens
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

// FUSE: RefreshingHttpClient is the most concurrency-sensitive piece
// in Phase 2. These tests are deterministic — a gated fake + a
// CompletableDeferred the test releases. NO delay(), no real time,
// no flakiness (the failure mode DATA_LAYER.md explicitly forbids).
@DisplayName("RefreshingHttpClient single-flight refresh")
class RefreshingHttpClientTest {

    // FUSE: gated fake transport. Throws Unauthorized until
    // `authorized` flips true (simulating a now-valid token after
    // refresh). Counts calls so retry behaviour is observable.
    private class GatedFake : HttpClient {

        var authorized = false
        var callCount = 0
            private set

        override suspend fun <T> get(path: String, deserializer: (String) -> T): T {
            callCount++
            if (!authorized) throw AppError.Unauthorized
            return deserializer("ok")
        }

        override suspend fun <T> post(
            path: String,
            jsonBody: String,
            deserializer: (String) -> T
        ): T {
            callCount++
            if (!authorized) throw AppError.Unauthorized
            return deserializer("ok")
        }
    }

    @Nested
    @DisplayName("Pass-through")
    inner class PassThroughTests {

        @Test
        fun `successful request does not trigger refresh`() = runTest {
            var refreshCount = 0
            val fake = GatedFake().apply { authorized = true }
            val client = RefreshingHttpClient(
                delegate = fake,
                tokenStore = InMemoryTokenStore(),
                refresh = { refreshCount++; AuthTokens.mock }
            )

            val result = client.get("/x") { it }

            assertEquals("ok", result)
            assertEquals(0, refreshCount)
            assertEquals(1, fake.callCount)
        }
    }

    @Nested
    @DisplayName("Refresh + retry")
    inner class RefreshRetryTests {

        @Test
        fun `Unauthorized triggers one refresh then a successful retry`() = runTest {
            var refreshCount = 0
            val fake = GatedFake()
            val store = InMemoryTokenStore()
            val client = RefreshingHttpClient(
                delegate = fake,
                tokenStore = store,
                refresh = {
                    refreshCount++
                    fake.authorized = true
                    AuthTokens.mock
                }
            )

            val result = client.get("/x") { it }

            assertEquals("ok", result)
            assertEquals(1, refreshCount)
            // first call (401) + retry (ok) == 2 transport calls
            assertEquals(2, fake.callCount)
        }

        @Test
        fun `refresh persists the new tokens to the store`() = runTest {
            val store = InMemoryTokenStore()
            val fake = GatedFake()
            val client = RefreshingHttpClient(
                delegate = fake,
                tokenStore = store,
                refresh = {
                    fake.authorized = true
                    AuthTokens.mock
                }
            )

            client.get("/x") { it }

            assertEquals(AuthTokens.mock, store.read())
        }

        @Test
        fun `retry still Unauthorized propagates and refreshes only once`() = runTest {
            var refreshCount = 0
            val fake = GatedFake()
            // refresh does NOT fix auth — the retry will 401 again.
            val client = RefreshingHttpClient(
                delegate = fake,
                tokenStore = InMemoryTokenStore(),
                refresh = { refreshCount++; AuthTokens.mock }
            )

            var thrown: Throwable? = null
            try {
                client.get("/x") { it }
            } catch (e: AppError.Unauthorized) {
                thrown = e
            }

            assertTrue(thrown is AppError.Unauthorized)
            // refreshed exactly once, retried exactly once, no loop
            assertEquals(1, refreshCount)
            assertEquals(2, fake.callCount)
        }

        @Test
        fun `slot clears so a later Unauthorized refreshes again`() = runTest {
            var refreshCount = 0
            val fake = GatedFake()
            val client = RefreshingHttpClient(
                delegate = fake,
                tokenStore = InMemoryTokenStore(),
                refresh = {
                    refreshCount++
                    fake.authorized = true
                    AuthTokens.mock
                }
            )

            client.get("/a") { it }
            assertEquals(1, refreshCount)

            // token "expires" again — a fresh wave must refresh anew
            fake.authorized = false
            client.get("/b") { it }
            assertEquals(2, refreshCount)
        }
    }

    @Nested
    @DisplayName("Single-flight")
    inner class SingleFlightTests {

        @Test
        fun `concurrent Unauthorized funnels into exactly one refresh`() = runTest {
            val gate = CompletableDeferred<Unit>()
            var refreshCount = 0
            val fake = GatedFake()
            val client = RefreshingHttpClient(
                delegate = fake,
                tokenStore = InMemoryTokenStore(),
                refresh = {
                    refreshCount++
                    // leader parks here until the test releases it,
                    // so all followers pile onto the SAME in-flight
                    // Deferred before any refresh completes.
                    gate.await()
                    fake.authorized = true
                    AuthTokens.mock
                }
            )

            val jobs = List(5) {
                async { client.get("/x") { it } }
            }

            // advance every coroutine to its suspension point: the
            // leader parked on `gate`, the 4 followers on the shared
            // Deferred. Exactly ONE refresh has begun.
            runCurrent()
            assertEquals(1, refreshCount)

            // release the single refresh; all 5 retry and succeed
            gate.complete(Unit)
            val results = jobs.awaitAll()

            assertEquals(listOf("ok", "ok", "ok", "ok", "ok"), results)
            assertEquals(1, refreshCount)
        }
    }
}
