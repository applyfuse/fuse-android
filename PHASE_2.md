# Phase 2 — Data Layer & Feed Feature (Android)

## 👋 If you are a fresh chat session, READ THIS FIRST

This branch (`phase-2`) is a self-contained handoff. You do **not**
need any prior chat history. Everything required to execute Phase 2
on `fuse-android` is in this file plus the existing repo.

**The iOS sibling (`fuse-ios`) already completed Phase 2.** Your job
is to mirror it on Android — same architecture, same decisions,
Kotlin/Compose idioms instead of Swift/SwiftUI. The iOS decision log
is reproduced below so you make the same choices without rediscovering
them.

**Workflow (important — conserves GitHub Actions minutes):**
- All commits land on THIS branch (`phase-2`) directly.
- CI runs LOCALLY on every push via `./scripts/ci-local.sh` (added
  in the same commit as this file).
- `.github/workflows/ci.yml` only triggers on `push`/`pull_request`
  to `main`, so pushing to `phase-2` does NOT spend Actions minutes.
- When ALL scope below is complete and local CI is green, open ONE
  `phase-2 → main` PR. That single PR is the only authoritative
  GitHub Actions run.
- The user runs `./scripts/ci-local.sh` and pastes results back.
  You diagnose from real output, never guess.

## Remaining scope

Mirror of fuse-ios Phase 2. Each row is one focused commit. Run
local CI between every commit.

| # | Item | Status | Notes |
|---|---|---|---|
| 1 | `AppError` expansion | ✔ | Forbidden, NotFound, Validation(message), Cancelled added. Commits b27e594/624729a/418fcfe. CI green. |
| 2 | `HttpClient` interface + Live + Fake | ✔ | bare OkHttp (NOT Retrofit) + Fake. Shape is get/post(deserializer), NOT send/sendRaw — both deviations recorded in DATA_LAYER.md decision log. Commits 7d3027b/03aba58/bef96c2/fbf365a/30e12e8 (+78668d9/9d677ef fixes). CI green. |
| 3 | `TokenStore` interface + InMemory + Live (EncryptedSharedPreferences) | ✔ | security-crypto 1.1.0-alpha06. Probe-and-skip for Live (coverage caveat logged). Commits e8a3b369/548fe8b/727dfcb/a4db5f7/0348876/da727a0. NOTE: AuthTokens/LiveTokenStore further modified in row 4 (expiresAt) and re-validated there. CI green. |
| 4 | `LiveAuthRepository` wired to HttpClient + TokenStore | ✔ | Thin coordination layer. login/logout(best-effort server)/currentUser. AuthTokens gained expiresAt (mirror iOS, Decision 1a). Flat auth wire types, internal+@Serializable (Decision 2). Commits 2d897ec/a76d602/5889a70/e3afdc2/6c1c94e/8c65837. Mirror iOS PR #10. **Built AFTER row 5 (build-order inversion). Hilt wiring pulled forward — see rows-4+10 unification note + DATA_LAYER.md.** Pending ci-local.sh re-validation of the combined set. |
| 5 | `RefreshingHttpClient` (401-refresh-retry, single-flight) | ✔ | Decorator. Mutex + shared CompletableDeferred, leader-runs-directly. No requiresAuth flag (Option 2). Recursion-safe via bare-client refresh. Commits 6f2a1ce/ae5dfb2/088e009 (+af523aa fix). Mirror iOS PR #11. CI green. **Built BEFORE row 4 (see inversion note).** |
| 6 | Feed: `FeedItem`, `FeedPage` domain + wire types | ☐ | Page-based. `FeedPage(items, page, hasMore)`. 1-indexed page. Server-authoritative has_more. |
| 7 | Feed: `FeedState`, `FeedAction`, `feedReducer` + reducer tests | ☐ | Pure function. `FeedLoadingState` enum (Idle/Initial/Refreshing/LoadingMore). ~40 reducer tests, zero mocks. 7 invariants (see iOS decision log). |
| 8 | Feed: `FeedViewModel` + VM tests | ☐ | Mirror AuthViewModel shape. `previousLoading` effect-firing guard. ~15 tests with FakeFeedRepository. |
| 9 | Feed: `FeedScreen` @Composable | ☐ | Thin reader. 4 content shapes: initial-load spinner / empty / list+load-more / error. |
| 10 | Feed: `LiveFeedRepository` (+ binding only — Hilt transport wiring ALREADY DONE) | ☐ | **REDUCED from original scope.** The Hilt transport graph (Json, OkHttp, bare LiveHttpClient, TokenStore, the shared @Singleton RefreshingHttpClient + refresh lambda) was pulled forward into `di/NetworkModule.kt` with row 4 — Hilt whole-graph compile-time validation made rows 4+10 inseparable (see unification note + DATA_LAYER.md). Row 10 now only: add `LiveFeedRepository(httpClient)` (HttpClient-only, no TokenStore), @Binds it in RepositoryModule, ~13 tests. It injects the SAME bound HttpClient (the singleton RefreshingHttpClient) → shares auth's refresh for free, automatically. |
| 11 | `DATA_LAYER.md` finalise | ☐ | A draft is committed alongside this file; update it as components land. Decision log already substantial — finalise the prose/wiring sections to match shipped code at the end. |
| 12 | `CLAUDE.md` Phase status + Detekt conventions | ☐ | Final doc commit before the PR. |
| 13 | (optional) Pagination primitives | — | DO NOT generalise. iOS decided against it (single consumer). Same call here. Recorded as a deliberate non-goal. |

**When every ☐ above is ✔ and local CI is green → open the
`phase-2 → main` PR. Not before.**

> **Row-numbering note (2026-05-19):** rows 1–5 are ✔. Rows 4 and 5
> were built in inverted order (5 before 4); harmless (dependency
> direction permits it), fully explained in the DATA_LAYER.md
> decision log ("Row 4/5 build-order inversion"). Additionally,
> row 10's Hilt transport wiring was pulled forward with row 4
> because Hilt validates the whole dependency graph at compile time
> — a repository with real deps cannot compile without its bindings
> in any order ("Rows 4+10 Hilt wiring unified" in the decision
> log). Row 10 is correspondingly reduced (see its row). The
> commit-message row numbers earlier in history reflect an internal
> working sequence; the decision log reconciles them. From row 6
> onward, commit numbering follows THIS table.

## Out of scope

- iOS work — separate repo (`applyfuse/fuse-ios`), Phase 2 already done.
- Phase 3 — not yet defined.
- Instrumented/espresso tests — Phase 2 is unit tests only (`./gradlew test`).
- Production signing / Play Store — separate concern.

## Local CI workflow

After checking out this branch, **once**:
```bash
chmod +x scripts/ci-local.sh
```

Then before/after every push:
```bash
./scripts/ci-local.sh
```

It runs the same three things `.github/workflows/ci.yml` runs on the
runner: `gradle test`, `gradle detekt`, `gradle assembleDebug`.
Output mirrors CI.

**Caveats (learned the hard way on iOS — Android equivalents):**

- **Keystore/EncryptedSharedPreferences in unit tests.** Robolectric
  or a JVM unit test has no Android Keystore. `LiveTokenStore` tests
  must use a probe-and-skip pattern (try a write in `@BeforeEach`,
  `Assumptions.assumeTrue(...)` / skip the suite if it throws) —
  exactly like iOS's `XCTSkip` Keychain probe. Don't let these be
  hard failures locally.
- **Coroutine scheduler determinism.** Concurrency tests (single-
  flight refresh) MUST be deterministic by structure, never
  `delay()`-based timing. Use `runTest` + `StandardTestDispatcher`
  + explicit `advanceUntilIdle()`, or a gated `CompletableDeferred`
  the test releases. The iOS single-flight test originally used a
  100ms sleep and flaked on contended runners — do NOT repeat that
  mistake. Gate it explicitly.
- **Data races on shared mutable test doubles.** A `FakeHttpClient`
  recording `sentRequests` from concurrent coroutines needs a
  `Mutex` or `synchronized`/`Collections.synchronizedList`, not a
  bare `mutableListOf`. iOS crashed locally on this exact pattern
  while passing on CI (different scheduler). Lock shared mutation
  in test doubles unless single-writer is provable.
- **JDK / Gradle drift.** CI pins JDK 17 + Gradle 8.9. If local
  uses a different JDK, Detekt or Kotlin compiler output can differ.
  Check `.github/workflows/ci.yml` for the pinned versions.
- **Detekt baseline.** Don't add a detekt baseline to silence new
  findings. Fix the finding or, if it's a genuine false positive,
  suppress narrowly with `@Suppress("RuleName")` + a comment.

## iOS Phase 2 decision log — MIRROR THESE

The iOS side already reasoned through these. Make the same calls.
Kotlin equivalents noted.

### ✔ Pagination model: page-based (NOT cursor, NOT Paging 3)

Greenfield, no backend constraint. Page-based:
- Simpler to test and reason about; fits FUSE's teaching focus.
- `FeedPage(items: List<FeedItem>, page: Int, hasMore: Boolean)`.
- `page` is 1-indexed (matches "page 3 of 12", no off-by-one).
- `hasMore` is server-authoritative — trust the server's signal,
  don't derive from `items.size == pageSize`.
- **Do NOT use Jetpack Paging 3.** FUSE's rule is "no libraries,
  clear mental model." Paging 3 is a library and hides the state
  machine. The CLAUDE.md "Phase 2 next" bullet mentions Paging 3 —
  that bullet is SUPERSEDED by this decision. The whole point is
  the reducer owns pagination state explicitly.
- Cursor migration later, if ever, is a 3-file local change
  (FeedPage, FeedState, FeedRepository). Not invasive.

### ✔ Reducer is a pure top-level function returning new state

iOS used `feedReducer(state:action:) -> State` (pure-functional,
not `inout`) for the Feed feature specifically, for test ergonomics:
`assertEquals(expected, feedReducer(state, action))` reads cleanly
and asserting input-unchanged is trivial.

Kotlin: `fun feedReducer(state: FeedState, action: FeedAction):
FeedState`. Pure. No coroutines, no IO. Matches the existing
`authReducer` style in this repo (verify by reading
`features/auth/AuthReducer.kt` — follow whatever signature shape it
already uses; consistency within fuse-android beats matching iOS
exactly).

### ✔ feedReducer invariants (all 7 — same as iOS)

1. Pure. No coroutines/IO/side effects. Same (state, action) in →
   same state out.
2. Total. Exhaustive `when` over the sealed `FeedAction`. New
   action → compile error until handled (use exhaustive `when`
   with no `else`).
3. Conservative no-op guards: loadInitial when items already exist
   → no-op; loadMore when hasMore=false → no-op; loadMore/refresh
   while loading → no-op (no concurrent loads policy — simplest
   model for teaching).
4. Loading state drives feedLoaded semantics: Initial/Refreshing →
   REPLACE items; LoadingMore → APPEND items; Idle → drop payload
   (stale-response defense).
5. Stale-response defense: feedLoaded while Idle is dropped.
6. Failures don't wipe items: feedFailed records error, returns to
   Idle, leaves items/page/hasMore untouched. A failed loadMore on
   page 2 must NOT empty the list.
7. Errors clear at the START of the next load attempt, not at the
   end of the previous one. UI shows error until retry or dismiss.

If the `when` in the reducer trips Detekt `CyclomaticComplexMethod`,
extract an `applyFeedLoaded(state, page)` private function — same
fix iOS used for `feedReducer`.

### ✔ ViewModel effect-firing guard: previousLoading

The reducer's no-op guards leave `loading` unchanged. If the VM
fired an effect for every action, no-ops would issue wasted network
calls. Capture `previousLoading` before calling the reducer; only
launch the effect coroutine if `state.loading != previousLoading &&
state.loading != Idle`. Filters reducer no-ops AND response actions
(feedLoaded/feedFailed go loading→Idle).

### ✔ LiveFeedRepository shares the refresh-protected client; no TokenStore

`LiveFeedRepository` takes only an `HttpClient`. Hilt wires it the
SAME `RefreshingHttpClient` instance `LiveAuthRepository` gets. A
401 on `GET /feed` then gets the identical single-flight refresh-
and-retry auth gets, for free, because the interceptor wraps the
transport not the repository. Feed never writes tokens → no
TokenStore dependency → minimal test surface (no fake token store
in LiveFeedRepository tests).

> **Realised (2026-05-19):** the shared client is the `@Singleton`
> `HttpClient` bound in `di/NetworkModule.kt` (= the
> `RefreshingHttpClient`). `LiveFeedRepository` at row 10 just
> `@Inject`s `HttpClient` and gets that exact singleton instance —
> the sharing is automatic, nothing extra to wire.

### — Pagination primitives: deliberately NOT generalised

Only one feature paginates. A generic `Paginated<T>` with one
consumer is speculative abstraction. Feature-local is correct until
a second paginated feature exists. Recorded so the absence is a
decision, not an oversight.

## Detekt & Kotlin-compiler conventions (translated from iOS lessons)

iOS hit 9 SwiftLint/compiler patterns. Kotlin/Detekt analogues to
start in the right shape:

- **`MagicNumber`** — Detekt flags bare numeric literals. Name them
  (`private const val DEFAULT_PAGE = 1`). Test files are usually
  excluded in detekt.yml; verify before assuming.
- **`LongMethod` / `CyclomaticComplexMethod`** — the reducer `when`
  can trip this. Extract `applyFeedLoaded` (mirror of iOS's helper
  extraction for the same rule).
- **`LongParameterList`** — wire data classes with many fields:
  prefer a single data class param over many positional params, or
  raise the threshold narrowly only if genuinely needed.
- **`TooManyFunctions`** — test classes with many `@Test` methods
  can trip this; detekt.yml usually excludes test sources. Verify;
  if not, exclude tests rather than merging tests into one method.
- **Coroutine test determinism** — the analogue of iOS's "XCTUnwrap
  autoclosure isn't async" and "single-flight must be gated, not
  slept": always `runTest`, never real `delay()` for ordering;
  use `StandardTestDispatcher` + `advanceUntilIdle()` or a gated
  `CompletableDeferred`.
- **`@Volatile`/visibility on shared test-double state** — analogue
  of iOS's `@unchecked Sendable` data race: a Fake recording calls
  from concurrent coroutines must lock its mutable list.
- **Hilt + test isolation** — analogue of iOS's `Environment`
  default-arg trap: prefer constructor injection so tests build
  ViewModels directly with fakes (no Hilt graph in unit tests).
  `@HiltViewModel` + `@Inject constructor` already enables this in
  this repo — follow the existing AuthViewModel pattern exactly.
- **`UnusedPrivateMember`** on preview/sample composables — if you
  add `@Preview` composables, Detekt may flag them; `@Preview`
  functions are conventionally fine, suppress narrowly if needed.

Full worked examples live in the fuse-ios CLAUDE.md "SwiftLint &
compiler conventions" section if deeper context is wanted, but you
should NOT need to read the iOS repo — the translations above are
sufficient.

## Test count expectation

Phase 1 baseline on `main`: read it from the existing
`app/src/test/...` tree (AuthReducerTest + AuthViewModelTest). After
Phase 2, expect roughly +60–70 tests (reducer ~40, VM ~15,
repositories ~25, error/http/token ~remainder), in the same
ballpark as iOS's 144 → ~212. Exact numbers don't matter; "every
state transition has an assertion, zero mocks in reducer tests"
does.

## Working style (from the iOS phase — keep it)

- One focused commit per scope row. Detailed commit messages
  explaining WHY, not just what. FUSE-style code comments prefixed
  `// FUSE:`.
- After each push, the user runs `./scripts/ci-local.sh` and pastes
  output. Diagnose from real output. Never guess; if a fix isn't
  obvious, read the relevant file before editing.
- Update this PHASE_2.md as rows complete (☐ → ✔) and add new
  decision-log entries for any non-obvious choice made along the
  way.
- Mirror iOS structurally but use idiomatic Kotlin/Compose/Hilt.
  Consistency within fuse-android > slavish parity with fuse-ios.
- The terse-confirm cadence is fine ("go", "both", "all green").
