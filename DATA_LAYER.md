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

> **Realised (2026-05-19):** this wiring is implemented in
> `di/NetworkModule.kt` (provides Json, OkHttpClient,
> `@Named("bare")` LiveHttpClient, TokenStore=LiveTokenStore, and
> the bound HttpClient=RefreshingHttpClient with the refresh
> lambda). Steps 4–5: `LiveAuthRepository` is bound via the
> existing `RepositoryModule`; `LiveFeedRepository` is bound there
> too (row 9) and injects the SAME bound HttpClient (the singleton
> RefreshingHttpClient) — sharing is automatic via `@Singleton`.
> See the "Rows 4+10 Hilt wiring unified" and "Row 9" decision-log
> entries.

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

### 2026-05-18 — Row 2: HttpClient shape = get/post(deserializer), NOT send(request)/sendRaw

**Decision / deviation — flagged late, recorded now.** `PHASE_2.md`
row 2 specifies the transport contract as `suspend fun <T>
send(request): T` + `sendRaw`, with an `HTTPRequest` value type and
a `requiresAuth:` flag (mirror of iOS `HTTPClientProtocol`). The
Android implementation instead shipped `suspend fun <T> get(path,
deserializer)` + `post(path, jsonBody, deserializer)` — no
`HTTPRequest` value type, deserializer passed per call, no
`requiresAuth`.

This is a real interface-shape deviation from the documented
contract. It was **not** flagged at row 2 (an omission in the
per-row discipline — corrected here as soon as it was noticed,
during row 4, while cross-checking the iOS source). It is validated
and behaviourally complete (rows 2–5 green), and is arguably more
idiomatic Kotlin (a request value object adds ceremony a
two-verb interface does not need here), but the divergence is now a
fact future readers must not trip over.

*Action for canon:* the docs/iOS owner should decide whether
`fuse-docs` records get/post as the Android-idiomatic realisation of
the `send`/`sendRaw` contract (recommended — the contract is about
"typed call → typed result or AppError", which both shapes satisfy),
or whether Android should be reshaped to `send`/`sendRaw` for strict
parity (costly — reopens rows 2–5). Surfaced, not buried; not
resolved unilaterally.

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

### 2026-05-18 — Row 5 (RefreshingHttpClient): no `requiresAuth` flag (Option 2); leader-runs single-flight

> **Heading correction (appended 2026-05-19):** the entry below was
> originally titled "Row 4". It concerns `RefreshingHttpClient`,
> which is **PHASE_2.md row 5**, not row 4. The mistitle came from
> an internal build-order numbering that did not match the
> authoritative `PHASE_2.md` scope table. See the
> "Row 4/5 build-order inversion" entry further down. Content
> unchanged; only the row number was wrong.

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

### 2026-05-19 — Row 4/5 build-order inversion (process note)

**What happened.** The internal working sequence built PHASE_2.md
**row 5** (`RefreshingHttpClient`) BEFORE PHASE_2.md **row 4**
(`LiveAuthRepository` wired to HttpClient + TokenStore). The scope
table's order is 4 then 5; the actual build order was 5 then 4.

**Why nothing is broken.** The dependency direction permits it:
`RefreshingHttpClient` decorates the `HttpClient` interface and has
zero dependency on `LiveAuthRepository`. `LiveAuthRepository`
depends on `HttpClient` (the interface) + `TokenStore`, not on
`RefreshingHttpClient` concretely (Hilt injects whichever
`HttpClient` impl — the shared refreshing one — at row 10). So both
rows are internally consistent regardless of build order, and each
was validated green via `ci-local.sh` independently.

**Why it is logged.** Deviating from the authoritative scope
ordering without surfacing it is exactly the silent-divergence
failure class this project's discipline exists to prevent. It was a
process miss (mine), caught when the real `PHASE_2.md` scope table
was finally read against the work. Recorded so the commit history's
row numbering (and the mistitled row-5 entry above) is explained,
not mysterious, to a future reader.

### 2026-05-19 — Row 4: AuthTokens gains `expiresAt` (mirror iOS); auth wire contract

**Decision 1a (user-confirmed) — `expiresAt` parity.** `fuse-ios`
`AuthTokens` is `{access, refresh, expiresAt: Date?}`. Android row 3
had shipped only `{accessToken, refreshToken}` — a cross-platform
divergence introduced without knowledge of the iOS shape. Row 4
closes it: Android `AuthTokens` now has `expiresAt: Long?` (epoch
millis, nullable, default null — additive/non-breaking). This
modified row-3-validated code (`AuthTokens`, `LiveTokenStore`
persistence, `InMemoryTokenStoreTest`); the whole row-4 set,
including those row-3 files, was re-validated via `ci-local.sh` —
row 3's prior green did NOT transfer.

Android's `RefreshingHttpClient` is **reactive** (401-driven, row 5),
so `expiresAt` is currently informational on Android — there is no
proactive-expiry refresh consuming it yet. It is mirrored anyway for
cross-platform parity and to avoid a future storage migration when a
proactive refresh is added. Same "don't strand a field the contract
implies" reasoning as the row-2 `Timeout` argument.

**Decision 2 (user-confirmed) — auth wire shape mirrors iOS.**
`POST /auth/login` → flat `LoginResponse { user, accessToken,
refreshToken, expiresIn? }` (NOT a nested `{ user, tokens:{} }`
envelope). `expiresIn` is seconds; converted to absolute epoch-millis
`expiresAt` at decode time so the app reasons in absolute time.
`logout` = best-effort `POST /auth/logout` (failure swallowed —
offline logout must work), THEN `TokenStore.clear()` whose failure
propagates. `currentUser` = guard on `tokenStore.read() == null` →
`null` with no network; else `GET /me`. All three mirror
`fuse-ios/Sources/Data/AuthRepository.swift` exactly. Wire types are
`internal`, `@Serializable`, kept out of `domain/model/` — mirror of
iOS's internal wire structs (the wire format is allowed to differ
from domain shape; the seam lives in one file).

`RefreshTokenRequest`/`RefreshResponse` wire types are included now
(next to the auth wire contract) though the refresh lambda that uses
them is wired in row 10's Hilt module — mirror of iOS keeping the
refresh wire shapes beside the login ones.

Completes PHASE_2 row 4: AuthTokens 2d897ec, LiveTokenStore
a76d602, LiveAuthRepository 5889a70, tests e3afdc2, this. NOT
validated until `ci-local.sh` is green on `phase-2` for this whole
set — same per-row discipline as rows 1–3 and 5.

### 2026-05-19 — Rows 4+10 Hilt wiring UNIFIED (latent scope-table flaw)

**What happened.** Giving `LiveAuthRepository` real
`(HttpClient, TokenStore)` constructor deps (row 4) made
`ci-local.sh` fail with `[Dagger/MissingBinding]`: Hilt validates
the **entire** dependency graph at compile time, and the existing
Phase-1 `RepositoryModule.bindAuthRepository` binds
`AuthRepository → LiveAuthRepository`, so the moment that class
needed `HttpClient`/`TokenStore` the whole app stopped compiling
until those were bound. Binding them is PHASE_2.md **row 10**.

**Conclusion — this is a latent flaw in the scope table, not just
execution.** PHASE_2.md lists row 4 (`LiveAuthRepository` wired) and
row 10 (Hilt `RepositoryModule` wiring) as independently shippable
rows. Hilt's whole-graph compile-time validation makes that
impossible: a repository with real deps cannot compile without its
bindings, in ANY build order. Rows 4 and 10's Hilt portion are one
atomic unit. (The earlier row-4/5 build-order inversion is unrelated
and a red herring here — row 4 hits this wall regardless of order.)

**Decision (user-confirmed).** Pull row 10's Hilt wiring forward
into `di/NetworkModule.kt`, landing WITH row 4. It is not premature
— the graph genuinely cannot compile without it, and a stopgap /
throwing binding (the considered alternative) was rejected as
exactly the "compiles green but broken at runtime" pattern this
project's whole discipline exists to eliminate. Row 10 is reduced
to: add `LiveFeedRepository` injecting the SAME bound `HttpClient`
(the `@Singleton` `RefreshingHttpClient`) — sharing is automatic.

**Process note (mine).** When scoping row 4 I chose to "defer Hilt
wiring to row 10" without recognising Hilt's whole-graph validation
makes that deferral impossible the moment the constructor changes.
A latent planning error, same category as the build-order
inversion. The gate caught it (the discipline working). Recorded,
not glossed.

**Decision — base URL.** `LiveHttpClient`'s `baseUrl` is provided
in `NetworkModule` as a private `const BASE_URL =
"https://api.applyfuse.com"`. A named const, NOT a
`BuildConfig`/flavor field: no gradle change, trivially swapped,
fits FUSE "clear mental model". Promote to a `BuildConfig` or
product-flavor field when a real staging-vs-prod split is needed —
recorded so the absence of build-config plumbing is a decision, not
an oversight.

**Refresh lambda.** Implemented in `NetworkModule.provideHttpClient`
using the BARE client (recursion safety): read current tokens
(none ⇒ propagate `Unauthorized` ⇒ user signed out), `POST
/auth/refresh` with `RefreshTokenRequest`, decode `RefreshResponse`,
persist + return rotated `AuthTokens` via the shared store. Uses the
wire types defined in row 4. The single shared `@Singleton`
`RefreshingHttpClient` is what every repository injects, so
401-refresh-retry is automatic across auth and (at row 10) feed.

NOT validated until `ci-local.sh` is green on `phase-2` for the
combined row-4 + NetworkModule set.

### 2026-05-19 — Row 6: Feed domain models — domain/wire seam (deviation from line-by-line iOS)

**Decision.** `FeedItem` and `FeedPage` land in `domain/model/` as
**pure Kotlin data classes with NO `@Serializable`** — mirroring
the `User` / `AuthTokens` convention, not iOS's
`FeedItem: Decodable` / `FeedPage: Decodable` (where wire and domain
are folded into one struct).

**Why this is a deliberate deviation, not an oversight.** iOS keeps
the wire format ON the domain model (`Decodable` conformance).
Android, from row 4 onward, established the OPPOSITE seam: domain
models are pure (`User`, `AuthTokens` carry no serialization);
`@Serializable` wire DTOs (`UserWire`, `LoginResponse`, …) live with
the repository. Copying iOS line-by-line here would put
`@Serializable` back onto the domain model and re-introduce exactly
the wire/domain coupling row 4 deliberately removed — an
*inconsistency within fuse-android*. `PHASE_2.md`'s working-style
rule is explicit: "Consistency within fuse-android > slavish parity
with fuse-ios." So the faithful mirror is structural, not literal:
same fields, same `mock`/`empty` companions, same locked pagination
contract (page 1-indexed, `hasMore` server-authoritative, no
`total`) — but pure domain types.

**"+ wire types" deferred to row 10, not dropped.** `PHASE_2.md`
row 6 reads "Feed: `FeedItem`, `FeedPage` domain **+ wire types**".
The `@Serializable` `FeedItemWire` / `FeedPageWire` are NOT in
row 6; they land with `LiveFeedRepository` (row 9, Option 1's
collapse of rows 9+10), in the repository file — exactly where the
auth wire types live relative to `LiveAuthRepository`. The row-4
pattern applied consistently; promise kept (see Row 9 entry).

**No standalone model tests for row 6.** `User` and `AuthTokens`
(equivalently trivial domain data classes) have ZERO standalone
test files in this repo; their behaviour is exercised via the
reducer/repository tests that consume them. `FeedItem`/`FeedPage`
follow that established precedent — their value semantics get
exercised by the feed reducer tests (row 7, ~40 tests) and the
`LiveFeedRepository` tests (row 9). Adding bespoke model tests here
would itself be an inconsistency. Recorded so the absence of a
`FeedItemTest`/`FeedPageTest` is a decision, not an oversight.

Completes PHASE_2 row 6: FeedItem ee57c9b, FeedPage 1e87417, this.
NOT validated until `ci-local.sh` is green on `phase-2` — same
per-row discipline as every prior row.

### 2026-05-19 — Row 7: FeedState shape — raw `AppError?` vs Auth's `String?` (intra-repo difference)

**Decision.** `FeedState.error` holds the raw `AppError?`.
`AuthState.errorMessage` (Phase 1) holds a pre-stringified
`String?` (its reducer calls `error.userMessage` and stores the
string). The two features deliberately differ.

**Why.** This mirrors iOS exactly — `fuse-ios FeedState.error` is
`AppError?`, `fuse-ios AuthState` carries a string. More
importantly, the PHASE_2.md feed-reducer invariants are written
against the raw error ("`feedFailed` records the error and returns
to `.idle`"), and invariant 6 ("failures don't wipe items")
reasons about the error case, not a message. Storing the raw
`AppError` keeps the reducer pure and string-free (it never
constructs user copy), and pushes message derivation to the edge —
row 8's `FeedViewModel` / row 9's `FeedScreen` call
`error.userMessage` where the UI actually needs it. Auth chose the
other split in Phase 1; both are internally valid. This is an
intra-repo *consistency* note, not a cross-platform deviation
(iOS does the same per-feature split). Recorded so a future reader
seeing `AuthState.errorMessage: String?` next to
`FeedState.error: AppError?` knows the difference is deliberate and
why, rather than guessing one is a mistake.

**`NOT_LOADED = 0` sentinel.** `currentPage` starts at `0` meaning
"nothing loaded yet"; real pages are 1-indexed (locked pagination
decision) so `0` can never collide. Exposed as a named
`companion const FeedState.NOT_LOADED` (not a bare `0`) so it is
not a `MagicNumber`, reads clearly in the reducer/tests, and row 8's
ViewModel can reference the same constant. Mirror of iOS's
`currentPage == 0` convention.

**`applyFeedLoaded` extraction.** `feedReducer` splits the
`FeedLoaded` branch into a private `applyFeedLoaded(state, page)`,
exactly as iOS split it and exactly as PHASE_2.md's Detekt-
conventions section prescribed ("the reducer `when` can trip
`CyclomaticComplexMethod`; extract `applyFeedLoaded`"). The natural
seam is outer action-dispatch vs the loading-state-driven
replace/append/drop decision. Behaviour identical; structural split
only — recorded so code-vs-contract readers see it was the
predicted, prescribed move, not an ad-hoc refactor.

Completes PHASE_2 row 7: FeedState 7f35061, FeedAction d4195f0,
feedReducer c807563, FeedReducerTest (41 tests) 927e326, this. NOT
validated until `ci-local.sh` is green on `phase-2` — same per-row
discipline as every prior row.

### 2026-05-19 — Row 8: FeedViewModel is standalone (Option C), NOT a BaseViewModel subclass

**Decision (user-confirmed, Option C).** `FeedViewModel` extends
`androidx.lifecycle.ViewModel()` directly and implements its own
`send()` / `handleEffect()` / `performLoad()` plus its own
`MutableStateFlow`. It deliberately does NOT extend `BaseViewModel`
(the in-repo `Store<S,A>` analogue that `AuthViewModel` uses).

**Why — the effect-firing guard.** `feedReducer`'s invariant-3
guards (LoadInitial when items exist, LoadMore when `hasMore=false`,
LoadMore/Refresh while loading) are NO-OPS: they return state with
`loading` UNCHANGED. The ViewModel must only spawn a network effect
when the reducer actually transitioned INTO a new loading state.
iOS's `FeedViewModel.send()` does this by capturing
`previousLoading` BEFORE the reducer, then
`guard loading != previousLoading, loading != .idle`.
`BaseViewModel.send()` is **final** and unconditionally launches
`handleEffect` AFTER the reducer with NO pre-reducer hook — so a
`BaseViewModel` subclass physically cannot see `previousLoading`.
Three options were weighed and the user chose C:

- **A — guard inside post-reducer `handleEffect` using only the
  post-reducer state.** Rejected: it cannot distinguish "reducer
  just transitioned me into LoadingMore (fire)" from "reducer left
  me in LoadingMore because it no-op'd a *concurrent* LoadMore
  (don't fire)". That is a real double-fire on rapid
  scroll-to-bottom — the common path — and shipping a known wasted
  duplicate request is exactly the silent-defect class this project
  refuses.
- **B — make `BaseViewModel.send()` open / add a pre-reducer
  hook.** Architecturally clean (default keeps auth unchanged) but
  it modifies Phase-1-validated SHARED core that the validated auth
  stack rides on, forcing auth re-validation and a "reopened
  validated core" record. Viable but higher blast radius.
- **C — standalone `FeedViewModel` with its own `send()`.**
  Chosen. Faithful mirror of iOS (whose `FeedViewModel` is itself a
  deliberately-standalone `ObservableObject` that does NOT route
  through the shared `Store`, for this exact reason). Leaves
  `BaseViewModel` / `AuthViewModel` byte-for-byte untouched — zero
  auth-regression risk. Cost: ~15 lines of state/`send()` plumbing
  are reimplemented rather than inherited. That duplication is the
  accepted price for not shipping A's bug and not reopening B's
  validated core. `PHASE_2.md` says "mirror AuthViewModel shape"
  but also "consistency within fuse-android > slavish iOS parity"
  and the iOS source itself documents FeedViewModel as
  intentionally NOT the shared store — so C is the iOS-faithful
  reading, with the deviation from "extend BaseViewModel" recorded
  loudly here rather than buried.

The guard implemented: `previousLoading = state.loading` → run
`feedReducer` → `if (newLoading == previousLoading || newLoading ==
Idle) return` → else launch effect. Condition 1 filters reducer
no-ops; condition 2 filters the response actions
(`FeedLoaded`/`FeedFailed`/`DismissError`, which return to `Idle`)
so a successful load does not recurse into another load. Pinned by
`FeedViewModelTest`'s "Effect-firing guard" tests, which assert on
`FakeFeedRepository.loadFeedCalls` (actual request behaviour, not
just state) — including the explicit
LoadMore-while-in-flight-does-not-double-fire case that is the
whole reason C exists.

### 2026-05-19 — Row 8: FeedRepository interface + Fake precede LiveFeedRepository (scope-ordering)

**Decision / scope-ordering resolution (surfaced, not buried).**
`PHASE_2.md` row 8 specifies "~15 tests with `FakeFeedRepository`",
but `FeedViewModel` (row 8) cannot compile or be tested without a
`FeedRepository` type to inject and a fake to test against — and
the *repository* is PHASE_2.md **row 10** (`LiveFeedRepository` +
`@Binds` + ~13 repo tests). This is the **same latent scope-table
coupling family** as the rows-4+10 Hilt issue: a later row owns a
type an earlier row structurally depends on.

Unlike rows 4+10 (where Hilt's whole-graph validation forced a
whole module forward), this one has a clean minimal resolution that
does NOT pull row 10's substance forward: the `FeedRepository`
**interface** + `FakeFeedRepository` are the minimal contract row 8
needs, so they land in row 8 (`data/repository/FeedRepository.kt`).
The substantive row-10 work — `LiveFeedRepository` (real
HttpClient-backed impl), its `@Serializable` `FeedItemWire` /
`FeedPageWire` DTOs, the Hilt `@Binds`, and ~13 repository tests —
all stays in row 10, added to the same file.

**Why this is the established pattern, not a new deviation.** This
mirrors EXACTLY how `AuthRepository` existed in Phase 1: the
interface + `FakeAuthRepository` shipped in Phase 1, and row 4 only
swapped the `LiveAuthRepository` BODY from stubs to real
coordination. Interface-and-fake-precede-live-impl is already how
this repo is structured (`AuthRepository.kt` holds interface + Live
+ Fake in one file). Row 8 introducing `FeedRepository` interface +
fake, with row 10 adding the Live body to the same file, is that
identical pattern applied to feed — not an ad-hoc reordering.

**Hilt deferral within row 8 (flagged).** `FeedViewModel` takes
`FeedRepository` as a constructor param but is NOT annotated
`@HiltViewModel`/`@Inject` yet. `FeedRepository` has no Hilt binding
until row 10's `LiveFeedRepository @Binds`; annotating
`@HiltViewModel` now — with nothing yet injecting `FeedViewModel`
(its injector, `FeedScreen`, is row 9) — would re-trigger the exact
rows-4+10 `[Dagger/MissingBinding]` whole-graph failure. With no
injection site and no annotation, `FeedViewModel` is simply not in
the Hilt graph and cannot trip validation; tests construct it
directly with `FakeFeedRepository`. Row 9/10 adds `@HiltViewModel
@Inject` + the `@Binds` together (a one-line change). Recorded so
the missing annotation reads as deliberate sequencing learned from
rows 4+10, not an oversight.

Completes PHASE_2 row 8: FeedRepository+Fake 9134d05, FeedViewModel
42923c4, FeedViewModelTest (16 tests) 738c9f3, this. NOT validated
until `ci-local.sh` is green on `phase-2` — same per-row discipline
as every prior row.

### 2026-05-19 — Row 9: Option 1 — rows 9+10 collapsed (LiveFeedRepository + binding + UI as one canonical unit)

**Decision (user-confirmed, Option 1).** Row 9 (`FeedScreen` UI)
and row 10 (`LiveFeedRepository` + Hilt `@Binds` + repo tests) are
built as ONE cohesive set. PHASE_2.md's row 10 is now empty
(folded into row 9).

**Why this is the canonical choice, grounded in
`fuse-docs/ARCHITECTURE.md` (NOT iOS-analogy).** This was decided
after the user asked to verify the approach against the SINGLE
SOURCE OF TRUTH. `fuse-docs/ARCHITECTURE.md` §5 "How to add a new
feature" is an explicitly ordered checklist: **step 5 Create
Repository + "Wire Hilt binding (Android)"**, step 6 ViewModel,
step 7 ViewModel tests, **step 8 Build the UI (last)**. The
PHASE_2.md row split (row 9 = UI, row 10 = repository binding)
*inverts* that canonical order — it would build step 8 before
step 5's binding. The same Hilt-whole-graph reality that forced the
rows-4+10 unification applies again: `FeedScreen`'s
`hiltViewModel()` needs `FeedViewModel @HiltViewModel` needs a
`FeedRepository` binding. Doing LiveFeedRepository → `@Binds` →
`@HiltViewModel` → UI as one ordered unit is therefore the
*contract-faithful* sequence, not a deviation. Option 2 (UI seam
now, binding in row 10) was viable and not against the contract,
but it would carry a temporary `FeedScreen` signature asymmetry vs
`AuthScreen` for one row; Option 1 has zero such wrinkle and
matches §5's ordering exactly.

**Authority correction (process note, mine).** Earlier rows
justified mirroring decisions as "mirror fuse-ios". Per
`ARCHITECTURE.md`'s "single source of truth" header and §3, the
cross-platform CONTRACT is `fuse-docs`, and `fuse-ios` is one
platform's *realisation* of it — a useful proxy, not the authority.
This entry and the row-9 commits reason from `fuse-docs` directly
(§5 ordering, §3 DI mapping, §7 Pattern 5 shared interceptor, §8
repository-test focus, §9 state snapshots). Prior "mirror iOS"
entries remain accurate as proxy reasoning; future entries cite
`fuse-docs` as the authority. Surfaced, not buried.

**FeedViewModel's two deviations from `fuse-docs` §3/§5/§6 — status.**
(1) *Standalone, not `BaseViewModel`* — remains, justified by the
effect-firing guard, under §1's explicit "pick the reducer/Store
shape per feature and note it" licence (iOS's Feed is likewise a
standalone, and §4 notes Feed uses the pure-functional reducer
variant — the contract expressly anticipates per-feature Store/
reducer variation). (2) *`@HiltViewModel` deferred (row 8)* — NOW
CLOSED by row 9: `@HiltViewModel @Inject` added after the `@Binds`
landed, so `FeedViewModel` is back on §3's DI mapping. Net: one
remaining, justified, contract-sanctioned deviation; the
sequencing deviation is resolved.

**What landed (6 commits).** (1) `LiveFeedRepository` + internal
`@Serializable` `FeedItemWire`/`FeedPageWire` DTOs with `toDomain()`
mappers, added to `FeedRepository.kt` (mirror of `AuthRepository.kt`
structure; the `@SerialName("has_more")` seam is explicit, no global
naming strategy). These DTOs are the "+ wire types" clause the
row-6 entry promised would land with `LiveFeedRepository` — promise
kept. (2) `RepositoryModule.bindFeedRepository` `@Binds @Singleton`
(same shape as `bindAuthRepository`); `LiveFeedRepository` injects
`HttpClient` → Hilt resolves the SAME `@Singleton`
`RefreshingHttpClient` → feed inherits 401-refresh free
(`fuse-docs` §7 Pattern 5; FUSE rule 6). (3) `@HiltViewModel
@Inject` on `FeedViewModel` (closes the row-8 deferral; safe now
the binding exists). (4) `FeedScreen` Compose UI — mirror of iOS
`FeedView`'s four content shapes + in-repo `AuthScreen` conventions;
`hiltViewModel()` default so the signature matches `AuthScreen`
exactly; raw `AppError?` → `userMessage` derived at the edge (the
row-7 promise kept); `@Preview` per state from `FeedState` company
fixtures. (5) `FeedRepositoryTest` (~14, `FakeHttpClient`, no
mocks; `fuse-docs` §8). (6) `MainActivity` start-destination wiring
+ this entry.

**MainActivity wiring scope (flagged).** `setContent {}` was empty
("Navigation host will go here in Phase 2"); `AuthScreen` is also
not yet wired. Row 9 wires `FeedScreen()` as the single start
destination inside `MaterialTheme`/`Surface` — the minimal correct
§5-step-8 "build the UI" wiring. A real `NavHost` (Auth → Feed) and
any custom `FuseTheme` are NOT introduced here (no nav-graph or
theme infrastructure exists in the repo yet, and inventing it would
exceed row 9's scope and the FUSE "no unnecessary infrastructure"
ethos). Recorded so a future reader sees the single-screen host is
a deliberate minimal choice pending the Profile feature (Phase 3),
not an oversight or a missing-NavHost defect.

Completes PHASE_2 rows 9 (+10, collapsed): LiveFeedRepository+DTOs
cbe18ca, bindFeedRepository b711cb9, FeedViewModel @HiltViewModel
5a1610f, FeedScreen 1aea76b, FeedRepositoryTest f7e356f, nav+this.
PHASE_2.md row 10 is now empty (folded here). NOT validated until
`ci-local.sh` is green on `phase-2` — same per-row discipline as
every prior row.
