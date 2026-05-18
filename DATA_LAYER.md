# FUSE Data Layer — Android

> **DRAFT — finalise as Phase 2 components land (scope row 11).**
> This is the Android mirror of fuse-ios's DATA_LAYER.md. The shape
> is intentionally identical; the idioms are Kotlin/OkHttp/Hilt.

## The shape

```
ViewModel  (AuthViewModel / FeedViewModel)
   │  calls
   ▼
Repository (LiveAuthRepository / LiveFeedRepository)
   │  builds request, decodes response
   ▼
RefreshingHttpClient            ← the interceptor/decorator
   │  • catches Unauthorized
   │  • single-flight refresh (Mutex + shared Deferred)
   │  • retries the original request once
   ▼
LiveHttpClient (bare)           ← Retrofit/OkHttp transport
   │  • attaches bearer token from TokenStore
   │  • maps status codes / IOExceptions → AppError
   ▼
OkHttp / network
```

The refresh call inside the Hilt module must use the **bare** client,
NOT the interceptor — recursion safety (a 401 on /auth/refresh must
NOT trigger another refresh). Mirror of the iOS recursion-safety
decision.

## Status-code → AppError mapping (mirror iOS exactly)

| HTTP status | AppError |
|---|---|
| 200..299 | (success) |
| 401 | `Unauthorized` |
| 403 | `Forbidden` |
| 404 | `NotFound` |
| 422 | `Validation(message)` — server envelope message if present |
| 400..499 | `ClientError(statusCode)` |
| 500..599 | `ServerError(statusCode)` |
| IOException (offline etc.) | `NetworkUnavailable` |

One shared configured JSON (Moshi or kotlinx.serialization — follow
whatever the Phase 1 repo already uses; check the version catalog
`gradle/libs.versions.toml` and existing code before adding a new
JSON lib). snake_case ↔ camelCase handled centrally.

## TokenStore

Interface: `suspend fun read(): AuthTokens?`, `suspend fun
write(tokens)`, `suspend fun clear()`. Two impls:
- `InMemoryTokenStore` — for tests/previews
- `LiveTokenStore` — EncryptedSharedPreferences (androidx.security.crypto)

Single shared instance in the Hilt graph: bare client reads it for
the Authorization header, the interceptor writes new tokens after
refresh, the auth repo writes on login / clears on logout.

## RefreshingHttpClient (single-flight)

Decorator implementing `HttpClient`. On `Unauthorized` from a
request with `requiresAuth = true`: run refresh, persist new tokens,
retry once. `requiresAuth = false` requests pass through.

Single-flight: a `Mutex` guards a nullable shared
`Deferred<AuthTokens>`. First caller in a wave creates the Deferred
and awaits it; concurrent callers await the same Deferred; after it
completes the slot clears so the next wave refreshes fresh. The
Kotlin analogue of iOS's nested `Gate` actor. No busy-wait, no
delay-based timing.

## Hilt wiring (Environment.live analogue)

`RepositoryModule` (or a dedicated `NetworkModule`) provides, in
order:
1. `LiveTokenStore` — singleton
2. bare `LiveHttpClient` — token provider reads the store
3. `RefreshingHttpClient` wrapping the bare client; its refresh
   lambda calls the BARE client
4. `LiveAuthRepository(httpClient = refreshing, tokenStore = store)`
5. `LiveFeedRepository(httpClient = refreshing)` — SAME refreshing
   instance, no token store

Both repositories share one `RefreshingHttpClient` → feed gets
401-refresh for free.

## Pagination (Feed)

Page-based. `GET /feed?page=N` → `{ items, page, has_more }`.
`page` 1-indexed. `has_more` server-authoritative. `page` sent as a
query param. **No Paging 3** — the reducer owns pagination state
explicitly (FUSE rule: no libraries, clear mental model).

## Testing the data layer

- Repositories: `FakeHttpClient` (scriptable result, records
  requests — lock the request list if concurrent). No network.
- `LiveHttpClient`: MockWebServer (okhttp3.mockwebserver) for real
  request/response/status mapping without a server.
- `RefreshingHttpClient`: a gated Fake + a CompletableDeferred the
  test releases for the single-flight test (deterministic, never
  delay()).
- `LiveTokenStore`: real EncryptedSharedPreferences with a
  probe-and-skip in @BeforeEach if the keystore is unavailable in
  the JVM test environment (analogue of iOS XCTSkip).
