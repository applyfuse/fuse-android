# FUSE Architecture — Android Project Context

## What is FUSE
FUSE (Feature-scoped Unidirectional State Engine) is a lightweight
unidirectional architecture pattern for native iOS and Android.
No libraries. No dependencies. Just a clear mental model.

Website: applyfuse.com
GitHub org: github.com/applyfuse

## The pattern
Action → Reducer → State → UI → Effect → (feeds back as Action)

## The 6 rules
1. Reducer is always a pure function — no async, no side effects
2. UI only reads state — never mutates directly
3. Effects live outside the reducer — in Store / ViewModel
4. State is the single source of truth per feature
5. Navigation is an Effect, never a State value
6. Repositories are interface bound — always mockable

## Android stack
- Kotlin / Jetpack Compose
- Abstract BaseViewModel<S, A> — MutableStateFlow
- Hilt for all DI — @HiltViewModel, @Inject
- Coroutines with viewModelScope — no RxJava
- MutableSharedFlow for one-time events (no replay)
- JUnit 5 + UnconfinedTestDispatcher for tests
- minSdk 26, targetSdk 35
- Jetpack Compose only — no XML layouts

## Folder structure
```
core/
  BaseViewModel.kt         Generic BaseViewModel<S,A>
  AppDispatchers.kt        Testable dispatchers
  AppError.kt              Typed error model
features/
  auth/
    AuthState.kt
    AuthAction.kt
    AuthReducer.kt
    AuthViewModel.kt
    AuthScreen.kt
data/
  repository/
    AuthRepository.kt      Interface + live + fake
di/
  RepositoryModule.kt      Hilt bindings
```

## Naming conventions
- States: [Feature]State (data class)
- Actions: [Feature]Action (sealed class)
- Reducers: [feature]Reducer() — always a function, never a class
- Effects: handled in ViewModel.handleEffect()
- Repositories: [Feature]Repository interface + Live[Feature]Repository + Fake[Feature]Repository

## Testing
- Run tests: ./gradlew test
- Run lint: ./gradlew detekt
- Reducer tests need zero mocks — pure function in, assert output
- Use UnconfinedTestDispatcher for ViewModel tests
- Always write reducer tests before UI

## Commit message format
feat(auth): add AuthReducer with tests
fix(viewmodel): handle cancellation in handleEffect
test(auth): add loginFailure reducer test
docs(readme): update run instructions

## Current phase
Phase 1 — Foundation (Week 1–2)
Day 1: Folder structure + scaffolding ✔
Day 2: BaseViewModel.kt + AppDispatchers.kt
Day 3: AuthRepository interface + live + fake + Hilt module
Day 4: AuthState + AuthAction
Day 5: AuthReducer + JUnit 5 suite
