# FUSE Data Layer — Android

> **This is the Android implementation-specific data-layer doc.** It
> documents how `fuse-android` concretely wires the data layer: the
> Hilt module composition, the `Mutex` + shared `Deferred`
> single-flight refresh, the EncryptedSharedPreferences token store.
>
> The **cross-platform conceptual contract** — the `HttpClient`
> interface shape, the error model, JSON conventions, and the locked
> cross-platform decisions — lives in the single source of truth:
> **https://github.com/applyfuse/fuse-docs/blob/main/DATA_LAYER.md**
>
> This file is **not** a copy of that one and **not** a stub: it is
> the real, Android-specific wiring, which legitimately differs from
> iOS's. If you change the *contract* (interface signatures, the
> error model, a locked decision), change it in `fuse-docs` — not
> here. If you change how *Android wires it*, change it here.
>
> **DRAFT — finalise as Phase 2 components land (PHASE_2.md scope
> row 11).** The wiring below is the intended Android realisation of
> the conceptual contract; confirm against the code as each
> component is implemented.

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

> The cross-platform pagination *contract* (page-based,
> server-authoritative `has_more`, 1-indexed) is recorded in the
> canonical `fuse-docs/DATA_LAYER.md`. The above is the intended
> Android realisation of it.

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

---

## Decision log

> Append-only. Each entry: what was decided, why, and any deviation
> from the cross-platform contract (`fuse-docs/DATA_LAYER.md`) that
> must be reconciled there.

### 2026-05-18 — Row 2: transport stack = bare OkHttp (NOT Retrofit)

**Decision.** The `HttpClient` transport (`LiveHttpClient`) is
implemented on **bare OkHttp 4.12.0**, not Retrofit.

**Deviation.** `PHASE_2.md` and the prose above ("Retrofit/OkHttp
transport") name Retrofit as the Android transport. This is a
deliberate, user-confirmed deviation. Rationale: FUSE's stated ethos
is "no libraries, no dependencies, clear mental model." Retrofit
hides the request → response → decode → `AppError.from()` path behind
annotation-driven interface proxies and converter factories — the
exact path a reference architecture should keep visible. Bare OkHttp
behind the hand-written `HttpClient` interface keeps that path
explicit. No behavioural difference to callers (the interface is
unchanged); this is an implementation-strategy choice only.

**Action for canon.** `fuse-docs/DATA_LAYER.md` and `PHASE_2.md`
should drop the "Retrofit" naming or note Android uses bare OkHttp.
Flagged for the docs owner; not changed here (this file is Android
wiring, not the cross-platform contract).

### 2026-05-18 — Row 2: JSON = kotlinx.serialization

**Decision.** JSON is **kotlinx.serialization 1.7.3** (+
`kotlin.plugin.serialization`), per the doc's instruction to "follow
whatever the Phase 1 repo already uses; check the catalog before
adding." Phase 1 used **no** JSON lib (verified: catalog had none).
kotlinx.serialization chosen in the Phase 2 setup decisions —
compile-time, no reflection, fits the "clear mental model" ethos.
No deviation from the contract (the contract left this open).

### 2026-05-18 — Row 2: IOException split (Timeout vs NetworkUnavailable)

**Decision.** `LiveHttpClient` maps `SocketTimeoutException` →
`AppError.Timeout` and all other `IOException` →
`AppError.NetworkUnavailable`.

**Deviation — needs canon reconciliation.** The mapping table above
(and `fuse-docs`, "mirror iOS exactly") lists a *flat*
`IOException → NetworkUnavailable` with no timeout split. The split
here is richer: `AppError` already has a distinct `Timeout` case
(present since Phase 1), a flat mapping would make `Timeout`
unreachable from the transport, and a timeout is materially
different UX from being offline ("try again" vs "check your
connection"). The `LiveHttpClientTest` asserts both arms.

This is a deliberate improvement, **but it diverges from a contract
that says mirror iOS exactly.** Two reconciliation paths, for the
docs/iOS owner to decide — NOT resolved unilaterally here:
1. Update `fuse-docs/DATA_LAYER.md` to split the row
   (`SocketTimeout → Timeout`, other `IOException →
   NetworkUnavailable`) and have iOS match — preferred, since iOS
   surely wants the same UX distinction.
2. Or collapse Android back to flat `NetworkUnavailable` to honour
   the existing contract strictly.

Flagged explicitly so this does not become a silent cross-platform
divergence. Pending that decision, Android implements the split
(option 1's intent) because reverting it would ship a worse UX and
strand the existing `Timeout` case.

### 2026-05-18 — Row 3: security-crypto 1.1.0-alpha06 (NOT 1.0.0 "stable")

**Decision.** `LiveTokenStore` uses
`androidx.security:security-crypto:1.1.0-alpha06`.

**Rationale / minor flagged deviation.** security-crypto's only
release on a "stable" track is 1.0.0, which has well-known
AndroidKeyStore reliability problems (key invalidation / decrypt
failures on certain API levels and after some device state changes).
1.1.0-alpha06 is the de-facto production-standard for
EncryptedSharedPreferences — years stable in wide use despite the
`alpha` label. Choosing it over the nominally-stable-but-buggy 1.0.0
is deliberate: FUSE wants the reliable path, and shipping a token
store on a version with known keystore bugs would be the worse call.
Not a cross-platform contract deviation (the contract names
EncryptedSharedPreferences, not a version); recorded here because
"use an alpha dependency" is itself a decision a future reader
deserves to see justified.

### 2026-05-18 — Row 3: LiveTokenStore tested via probe-and-skip — COVERAGE CAVEAT

**Decision.** `LiveTokenStoreTest` uses the probe-and-skip strategy
this doc's "Testing the data layer" section already prescribes
(user-confirmed): `@BeforeEach` attempts real construction and
forces the lazy `EncryptedSharedPreferences` to initialise; if the
Android Keystore is unavailable, JUnit5 `assumeTrue` SKIPS the tests
(the Android analogue of iOS `XCTSkip`).

**Caveat — stated so it is NOT a silent gap.** In `ci-local.sh`'s
plain-JVM run there is no Android runtime, so `LiveTokenStoreTest`
**always skips** — meaning **LiveTokenStore's real AES-256
encryption round-trip is NOT exercised by the JVM CI gate.**
`InMemoryTokenStore` is fully tested (pure JVM); `LiveTokenStore`'s
encryption path is only exercised on an instrumented/emulator run,
which the current CI does not perform.

This is contract-consistent and was explicitly chosen over the
alternatives (Robolectric — rejected as a heavy new test dependency
against FUSE's "no libraries" ethos; or not testing LiveTokenStore
at all). But it is logged loudly here, in the file's own decision
log, precisely because "a security-critical class whose core path
has no executing assertion in CI" is the exact failure class this
project spent its Phase 1 remediation eliminating. Recommended
follow-up for whoever owns CI: add an instrumented-test job
(emulator) so the encryption round-trip is actually verified
somewhere, even if not in `ci-local.sh`. Until then, this gap is
known and accepted, not hidden.

### 2026-05-18 — Row 4: no `requiresAuth` flag (Option 2); leader-runs single-flight

**Decision A — auth scoping (Option 2, user-confirmed).** The
`HttpClient` interface keeps its row-2 signature: NO `requiresAuth:
Boolean` parameter. `RefreshingHttpClient` treats *every* call
through it as auth-bearing.

The prose in "RefreshingHttpClient (single-flight)" above still
mentions `requiresAuth = true/false`. That phrasing is **superseded
for Android** by this decision. Recursion safety does not need a
per-call flag: it comes entirely from the contract's existing
mechanism — the `refresh` lambda calls the **bare** `HttpClient`,
never the decorator, so a 401 on `/auth/refresh` cannot re-enter
refresh logic. "Every call through the decorator wants auth; the one
call that must not (the refresh itself) bypasses the decorator by
construction" is the cleaner mental model for a reference
architecture, and it avoids reopening the already-validated row-2
interface and all its call sites.

*Action for canon:* `fuse-docs/DATA_LAYER.md` should drop the
`requiresAuth` language for Android (or confirm iOS also drops it).
Flagged for the docs owner; the cross-platform contract is theirs to
change, not this file's.

**Decision B — single-flight structural note.** The contract says
"first caller creates the Deferred **and awaits it**". The
implementation has the leader create a `CompletableDeferred`, then
**run `refresh()` directly** and `complete()` it — rather than
launching the refresh into a scope and awaiting its own job.
Concurrent callers still await the shared Deferred exactly as
specified.

Rationale: the await-your-own-launched-job variant needs a
long-lived `CoroutineScope` to host the `async`, and risks a
self-await if mis-scoped. The leader-runs-directly variant is
**scope-free** (nothing to inject or leak), deadlock-free, and has
*identical* single-flight semantics: exactly one refresh per wave,
followers share it, the slot clears (`===`-checked) after
completion so a later wave refreshes fresh. This is a structural
implementation choice, not a behavioural deviation — recorded so a
future reader comparing code to contract sees it was deliberate.

Completes row 4: RefreshingHttpClient 6f2a1ce, tests ae5dfb2, this.
Row 4 is NOT validated until ci-local.sh is green on phase-2 — same
per-row discipline as rows 1–3.