package com.applyfuse.fuse.data.network

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.data.token.TokenStore
import com.applyfuse.fuse.domain.model.AuthTokens
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// FUSE: RefreshingHttpClient is a DECORATOR over HttpClient. It adds
// exactly one behaviour: when a request comes back Unauthorized,
// refresh the token once (single-flight) and retry the request once.
//
// Recursion safety (DATA_LAYER.md contract): the `refresh` lambda
// MUST call the BARE HttpClient, never this decorator. A 401 on
// /auth/refresh therefore cannot re-enter refresh logic — it just
// propagates. This is why the interface needs no `requiresAuth`
// flag (design decision: Option 2): every call through THIS client
// is auth-bearing by construction, and the one call that must NOT
// trigger refresh (the refresh itself) bypasses the decorator
// because it goes through the bare client.
//
// Single-flight: a Mutex guards a nullable shared
// CompletableDeferred<AuthTokens>. The FIRST caller in a wave (the
// "leader") creates the Deferred, performs the real refresh, writes
// the tokens, and completes the Deferred. CONCURRENT callers see
// the existing Deferred and await it instead of refreshing again.
// After completion the slot clears, so a LATER Unauthorized starts
// a fresh refresh. No busy-wait, no delay()-based timing — the
// Kotlin analogue of iOS's nested Gate actor.
//
// Structural note (recorded in DATA_LAYER.md decision log): the
// contract says "first caller creates the Deferred and awaits it".
// Here the leader RUNS refresh() directly and completes the
// Deferred rather than awaiting its own launched job — functionally
// identical single-flight, but scope-free (no long-lived
// CoroutineScope needed) and free of any self-await deadlock risk.
class RefreshingHttpClient(
    private val delegate: HttpClient,
    private val tokenStore: TokenStore,
    private val refresh: suspend () -> AuthTokens
) : HttpClient {

    private val refreshMutex = Mutex()
    private var inFlight: CompletableDeferred<AuthTokens>? = null

    override suspend fun <T> get(path: String, deserializer: (String) -> T): T =
        withSingleRetry { delegate.get(path, deserializer) }

    override suspend fun <T> post(
        path: String,
        jsonBody: String,
        deserializer: (String) -> T
    ): T =
        withSingleRetry { delegate.post(path, jsonBody, deserializer) }

    // FUSE: run the call; on Unauthorized refresh once and retry
    // ONCE. A second Unauthorized (or anything else) from the retry
    // propagates untouched — no loops, exactly one retry.
    private suspend fun <T> withSingleRetry(call: suspend () -> T): T =
        try {
            call()
        } catch (e: AppError.Unauthorized) {
            refreshTokens()
            call()
        }

    // FUSE: single-flight token refresh. Returns once fresh tokens
    // exist and are persisted. ReturnCount kept at 2 (follower
    // early-return + leader return); the catch rethrows, it does
    // not add a return.
    private suspend fun refreshTokens(): AuthTokens {
        val deferred: CompletableDeferred<AuthTokens>
        val isLeader: Boolean
        refreshMutex.withLock {
            val existing = inFlight
            if (existing == null) {
                deferred = CompletableDeferred()
                inFlight = deferred
                isLeader = true
            } else {
                deferred = existing
                isLeader = false
            }
        }

        if (!isLeader) {
            return deferred.await()
        }

        try {
            val tokens = refresh()
            tokenStore.write(tokens)
            deferred.complete(tokens)
            return tokens
        } catch (t: Throwable) {
            // FUSE: surface the failure to every follower awaiting
            // this same Deferred, then rethrow for the leader.
            deferred.completeExceptionally(t)
            throw t
        } finally {
            // FUSE: clear the slot ONLY if it still points at our
            // Deferred, so a refresh wave that started after us is
            // not wiped. Next Unauthorized then refreshes fresh.
            refreshMutex.withLock {
                if (inFlight === deferred) {
                    inFlight = null
                }
            }
        }
    }
}
