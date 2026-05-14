# FUSE Architecture — Android Project Context

## What is FUSE
FUSE (Feature-scoped Unidirectional State Engine) is a lightweight
unidirectional architecture pattern for native iOS and Android.
No libraries. No dependencies. Just a clear mental model.

Website: applyfuse.com
GitHub org: github.com/applyfuse
Docs: github.com/applyfuse/fuse-docs

## The pattern
Action → Reducer → State → UI → Effect → (feeds back as Action)

## The 6 rules
1. Reducer is always a pure function — no coroutines, no side effects
2. UI only reads state — never mutates directly
3. Effects live outside the reducer — in handleEffect()
4. State is the single source of truth per feature
5. Navigation is an Effect — never a State value
6. Repositories are interface bound — always mockable

## Android stack
- Kotlin / Jetpack Compose
- Abstract BaseViewModel<S,A> — MutableStateFlow
- Hilt for DI — @HiltViewModel, @Inject
- Coroutines with viewModelScope
- MutableSharedFlow (no replay) for one-time events
- JUnit 5 + UnconfinedTestDispatcher for tests
- minSdk 26, targetSdk 35
- Jetpack Compose only — no XML layouts
- Coroutines only — no RxJava

## Folder structure
```
app/
  src/
    main/
      AndroidManifest.xml
      java/com/applyfuse/fuse/
        FuseApplication.kt           @HiltAndroidApp
        MainActivity.kt              @AndroidEntryPoint
        core/
          BaseViewModel.kt           abstract class
          AppDispatchers.kt          injectable dispatchers
          AppError.kt                sealed class
        features/
          auth/
            AuthState.kt             data class + computed
            AuthAction.kt            sealed class + AuthEvent
            AuthReducer.kt           pure function
            AuthViewModel.kt         @HiltViewModel
            AuthScreen.kt            @Composable
        data/
          repository/
            AuthRepository.kt        interface + live + fake
        domain/
          model/
            User.kt
        di/
          RepositoryModule.kt        Hilt @Binds
      res/
        values/
          strings.xml
          themes.xml
    test/
      java/com/applyfuse/fuse/
        features/
          auth/
            AuthReducerTest.kt       zero mocks
            AuthViewModelTest.kt     fake repository
gradle/
  libs.versions.toml               version catalog
  wrapper/
    gradle-wrapper.properties
build.gradle.kts                    root build
settings.gradle.kts
gradle.properties
detekt.yml
```

## Naming conventions
- States: [Feature]State (data class)
- Actions: [Feature]Action (sealed class)
- Reducers: [feature]Reducer() — always a function, never a class
- Repositories: [Feature]Repository interface + Live + Fake
- Events: [Feature]Event (sealed class conforming to FuseEvent)

## Testing commands
```bash
# Run all unit tests
./gradlew test

# Run Detekt
./gradlew detekt

# Run tests + lint together
./gradlew test detekt

# Build debug APK
./gradlew assembleDebug
```

## Commit message format
```
feat(auth): add AuthReducer with tests
fix(viewmodel): handle cancellation in handleEffect
test(auth): add loginFailure reducer test
docs(readme): update run instructions
ci: update JDK version in workflow
```

## Phase status
Phase 1 — Foundation ✔ COMPLETE

Phase 2 — Data layer (next)
- Real network calls via Retrofit
- Token storage in EncryptedSharedPreferences
- Token refresh interceptor (OkHttp Authenticator)
- Pagination in Feed feature (Paging 3)
