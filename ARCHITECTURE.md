# FUSE Architecture

> **FUSE — Feature-scoped Unidirectional State Engine.**
> A lightweight unidirectional architecture for native iOS and
> Android. No libraries. No dependencies. Just a clear mental model.
>
> This document is the **canonical, platform-neutral statement of
> the pattern**. The conceptual content is identical in
> `applyfuse/fuse-ios` and `applyfuse/fuse-android`; only the
> "Platform mapping" appendix at the end differs per repo. This is
> the *as-built* architecture as of Phase 2 (data layer + Feed),
> not a plan-time sketch — where the original idea evolved during
> implementation, this reflects what actually shipped.
>
> Roadmap: see `FUSE_PLAN.md`. Per-platform implementation context:
> see `CLAUDE.md`. Data layer specifics: see `DATA_LAYER.md`.

## The one-sentence model

```
Action → Reducer → State → UI → Effect → (feeds back as Action)
```

A user interaction (or a system event) becomes an **Action**. A pure
**Reducer** folds that Action and the current **State** into a new
State. The **UI** renders the new State. Some Actions also trigger
an **Effect** (network, storage, navigation) whose result comes back
into the loop as another Action. The loop is the whole architecture.

## The 6 rules

These are not style preferences. They are the load-bearing
invariants; breaking one breaks the guarantees the rest depend on.

1. **The reducer is always a pure function** — no async, no I/O, no
   side effects. Same `(state, action)` in → same state out, every
   time. This is what makes the system testable without mocks and
   debuggable by inspection.
2. **The UI only reads state — it never mutates it directly.** The
   UI's only write capability is dispatching an Action. There is no
   back door.
3. **Effects live outside the reducer** — in the ViewModel/Store
   effect handler. Anything that talks to the network, disk, clock,
   or navigation stack is an Effect, never reducer code.
4. **State is the single source of truth per feature.** Each
   feature owns one State value. The UI is a pure function of it.
   Nothing meaningful lives only in the view layer.
5. **Navigation is an Effect — never a State value.** A route is
   something that *happens*, not something a feature *stores*. The
   current screen is not a field in `AuthState`. (Routing
   infrastructure may hold a navigation path; that infrastructure
   is not feature State — see Phase 3 / `NAVIGATION.md` when it
   lands.)
6. **Repositories are protocol/interface bound — always mockable.**
   Features depend on an abstraction, never a concrete network or
   storage class. The live implementation and the test double are
   interchangeable by construction.

## The pieces

### State
A plain value type (struct / data class), `Equatable`, owned by
exactly one feature. Holds everything the UI needs to render and
nothing it doesn't. Derived values are computed properties on the
State, not stored fields the reducer has to keep in sync.

### Action
An enumerable, exhaustive set (enum / sealed class) of everything
that can happen to a feature: user intents (`loginTapped`),
lifecycle (`loadInitial`), and effect responses (`feedLoaded`,
`feedFailed`). Exhaustiveness is enforced by the compiler — a new
Action is a compile error until the reducer handles it.

### Reducer
A free function — never a class, never a method on a stateful
object: `reducer(state, action) -> state`. Pure (rule 1). Total
(handles every Action). Conservative: an Action that doesn't make
sense in the current state is a no-op returning the state
unchanged, not a crash and not an invalid mutation. The reducer is
the *only* place state transitions are defined, so the reducer
tests *are* the behavioural spec.

### UI
A pure projection of State (SwiftUI `View` / Compose `@Composable`).
Reads State, renders, and dispatches Actions on interaction. Holds
no business logic and no state of its own beyond ephemeral view
concerns the architecture doesn't care about (scroll position,
focus). If the UI is deriving domain logic, that logic belongs in
the reducer or a computed State property.

### Effect
Async work, isolated from the reducer, run by the ViewModel/Store
after the reducer has produced the new State. An Effect's *result*
re-enters the loop as an Action — it never mutates State directly.
The effect handler switches on the Action and performs the matching
side effect; it derives nothing about *whether* to run from
re-deriving state, only from the Action and the post-reducer State.

### Repository
The boundary between a feature and the outside world (network,
storage). An interface with a live implementation and a test fake.
Features hold the interface type. The composition root decides
which implementation is wired in. This is what lets reducer and
ViewModel tests run with zero network and zero mocks of
infrastructure.

### Event channel (one-time effects)
State is for *what is*; some things are *what just happened once* —
a navigation transition, a transient error toast. Those are
**one-time events**, delivered through a dedicated event channel
(not stored in State, because re-rendering must not replay them).
A feature defines its own event type conforming to a shared
`FuseEvent` marker; the engine exposes an `emit`/subscribe channel
distinct from the State stream. Navigation (rule 5) flows through
*this*, not through State.

## Why these constraints pay off

- **Testability without mocks.** Reducer tests are
  `assertEqual(expected, reducer(state, action))`. No test doubles,
  no setup, no async. The data layer is mocked only at the
  repository seam, and only for ViewModel/integration tests.
- **Debuggability by inspection.** State is one value. A bug is
  "given this State and this Action, the reducer produced the wrong
  State" — reproducible from two values, no stepping through
  call stacks.
- **Concurrency stays in one place.** All async is in effect
  handlers. The reducer is pure, so it is trivially thread-safe and
  order-independent. Concurrency bugs have one place to live, not N.
- **Features compose without coupling.** Each feature is a
  self-contained State/Action/Reducer/Effect quad behind a
  repository interface. Cross-feature concerns are explicit (a
  shared event channel / root state in later phases), never
  ambient.

## The testing philosophy

- **Reducer tests: zero mocks, zero async.** Every state transition
  has at least one assertion. The reducer test file is the readable
  specification of the feature's behaviour.
- **ViewModel/integration tests:** the repository is faked at its
  interface; effects are driven and the resulting State asserted.
  Concurrency tests are made deterministic *by structure* (explicit
  ordering primitives), never by sleeping on a wall clock.
- **No library is introduced to "help" with any of the above.** The
  pattern's value is that it needs none. Adding one to paper over a
  rough edge is a regression, not a convenience.

## What FUSE deliberately is NOT

- Not Redux/TCA-with-a-library. The point is the *mental model*
  reproduced in plain platform code, not a framework dependency.
- Not an excuse for speculative abstraction. Generic machinery is
  introduced only when a *second* real consumer exists, never on
  spec. (E.g. pagination was kept feature-local while only one
  feature paginated — recorded as a deliberate non-goal, not an
  oversight.)
- Not "navigation as state." Worth repeating because it is the most
  common way the pattern gets quietly broken.

---

## Platform mapping — Android (`fuse-android`)

The pattern above is realised in this repo with these concrete
types. (iOS maps the same concepts to Swift idioms — see the
corresponding section in `fuse-ios`'s `ARCHITECTURE.md`.)

| Concept | Android realisation |
|---|---|
| Engine | `BaseViewModel<S, A>` — abstract `ViewModel` subclass (`app/src/main/java/com/applyfuse/fuse/core/BaseViewModel.kt`) |
| State stream | `MutableStateFlow` exposed as read-only `StateFlow<S>`; Compose collects via `collectAsStateWithLifecycle()` |
| Dispatch | `fun send(action: A)` — `_state.update { reduce(it, action) }` runs synchronously, then `handleEffect` is launched in `viewModelScope` |
| Reducer | abstract `reduce(state: S, action: A): S`, implemented by each feature delegating to its free `…Reducer` function; pure, exhaustive `when` over the sealed Action |
| UI | Jetpack Compose `@Composable`, collects `state`, calls `vm.send(…)` |
| Effect | `open suspend fun handleEffect(action: A, state: S)`, run in `viewModelScope` after the reducer, result re-dispatched as an Action |
| Repository | `interface` + `Live…` + `Fake…`, bound via Hilt `@Binds` in `di/RepositoryModule.kt` |
| One-time events | `MutableSharedFlow<FuseEvent>(extraBufferCapacity = 1)` exposed as `SharedFlow`; `suspend fun emit(event:)`; `interface FuseEvent`. No replay → events fire exactly once (navigation in Phase 3 builds on *this*, not a new channel) |
| Composition root | Hilt modules (`@HiltAndroidApp`, `@HiltViewModel`, `@Inject constructor`); fakes injected directly in unit tests, no Hilt graph needed |
| Concurrency | Kotlin coroutines; `viewModelScope` (auto-cancelled in `onCleared`); injectable `AppDispatchers` for test determinism |

Stack specifics (minSdk/targetSdk, Compose-only, Gradle version
catalog, Detekt, the local-CI workflow, and the catalogued
Detekt/Kotlin pitfalls) live in this repo's `CLAUDE.md`. Data-layer
wiring (Retrofit transport, EncryptedSharedPreferences token store,
refresh interceptor) lives in `DATA_LAYER.md` — note that on
`fuse-android`, the data layer itself is Phase 2 work being executed
from this branch; `DATA_LAYER.md` here is a draft that finalises as
those components land (see `PHASE_2.md` scope row 11).
