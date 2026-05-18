# FUSE Architecture — Full Execution Plan

> Canonical roadmap, committed to the repo so every phase handoff
> references the same source of truth rather than a paraphrase.
> Transcribed from the original `FUSE Plan.rtf`. The Phase **goals
> and bullets below are the shared cross-platform contract** and are
> intentionally identical to the copy in `applyfuse/fuse-ios`. Only
> the **status annotations** differ per platform — this file tracks
> **fuse-android**; fuse-ios tracks the same phases independently in
> its own repo.

## Phase 1 — Foundation (Week 1–2) ✔ COMPLETE (fuse-android)

Goals: Core engine working, one feature end-to-end

- Write `Store<State, Action>` on iOS
- Write `BaseViewModel<State, Action>` on Android
- Define Environment / Hilt module structure
- Set up folder structure (Core / Features / Data / DI)
- Write first feature: Auth (State, Action, Reducer, Effect, View)
- Write reducer unit tests for Auth
- Set up SwiftUI Preview and Compose Preview with mock environment
- Commit ARCHITECTURE.md explaining the pattern

## Phase 2 — Data layer (Week 3–4) ◀ CURRENT (fuse-android)

Goals: Real network calls flowing through the architecture

- Define AppError sealed type on both platforms
- Implement AuthRepository protocol + live implementation
- Add token storage (Keychain / EncryptedSharedPreferences)
- Implement token refresh interceptor
- Add MockAuthRepository for tests
- Write ViewModel / Store integration tests with mock repo
- Add network reachability monitor feeding into state

> **fuse-android status:** scaffolded on the `phase-2` branch
> (handoff package: this file, `PHASE_2.md`, `DATA_LAYER.md` draft,
> `scripts/ci-local.sh`). NOT yet implemented, NOT merged to `main`
> — `main` is still at Phase 1. The detailed execution plan and the
> iOS decision log to mirror are in **PHASE_2.md** on this branch.
>
> **fuse-ios reference:** completed Phase 2 (PR #15), and also
> shipped the Feed feature + page-based pagination in the same phase
> (slightly ahead of the plan, which lists Feed under Phase 4).
> fuse-android Phase 2 mirrors that combined scope — see PHASE_2.md
> scope rows 6–10 for the Feed work. The "network reachability
> monitor" bullet was NOT implemented on iOS either; treat it the
> same way (deferred, tracked) unless decided otherwise with the
> user.

## Phase 3 — Navigation & routing (Week 5)

Goals: Full navigation working with deep links

- iOS: Router as ObservableObject in environment, NavigationStack
  with typed routes
- Android: Compose Navigation with sealed Screen destinations
- Implement Effect channel for one-time navigation events
  (SharedFlow / PassthroughSubject)
- Add deep link handling on both platforms
- Add auth guard (redirect to login if unauthenticated)

> **fuse-android status:** not started. (fuse-ios has this scaffolded
> on its own `phase-3` branch; the fuse-android Phase 3 handoff,
> when it happens, will mirror whatever fuse-ios ships there — same
> pattern as the Phase 2 handoff.)

## Phase 4 — Feature expansion (Week 6–8)

Goals: 3–5 full features built on the pattern

- Add second and third features (e.g. Feed, Profile, Settings)
- Implement root AppState with fan-out reducer
- Add cross-feature state (e.g. cart reads from auth state)
- Add AppEvent global effect channel for cross-feature events
- Implement pagination in at least one list feature
- Add optimistic updates in at least one mutation

> Feed + pagination land in fuse-android Phase 2 (mirroring iOS,
> which shipped them in its Phase 2). Phase 4 here is therefore
> Profile/Settings + root AppState fan-out + cross-feature state +
> optimistic updates.

## Phase 5 — Quality & tooling (Week 9–10)

Goals: Production-ready code quality pipeline

- Add SwiftLint + Detekt to CI
- Add Fastlane lanes: test, build, deploy to TestFlight / Play
  Internal
- Add Firebase Crashlytics on both platforms
- Add analytics protocol + Firebase Analytics implementation
- Add debug menu (environment switcher, cache reset, feature flag
  overrides)
- Set up multi-environment configs (dev / staging / prod)

> Detekt is already in CI on fuse-android (Phase 1). Phase 5 here is
> Fastlane + Crashlytics + analytics + debug menu + multi-env.

## Phase 6 — Hardening (Week 11–12)

Goals: Ready for real users

- Accessibility audit (Dynamic Type, VoiceOver, TalkBack)
- Keyboard avoidance on all form screens
- Safe area handling audit on all screens
- Memory leak audit (Instruments / LeakCanary)
- Cold start time measurement and optimisation
- Write CHANGELOG.md and set up semantic versioning
- Phased rollout plan documented in Fastlane
