# FUSE Android

> Android reference implementation of the FUSE architecture pattern.
> Kotlin · Jetpack Compose · Coroutines · Hilt · No extra dependencies.

🌐 [applyfuse.com](https://applyfuse.com) · 📖 [Architecture docs](https://github.com/applyfuse/fuse-docs)

---

## Stack

- minSdk 26 / targetSdk 35
- Kotlin 2.0+
- Jetpack Compose
- Coroutines + Flow
- Hilt
- JUnit 5

## Structure

```
app/src/main/java/com/applyfuse/fuse/
  FuseApplication.kt     @HiltAndroidApp entry point
  MainActivity.kt        @AndroidEntryPoint shell
  core/                  BaseViewModel, AppDispatchers, AppError
  features/              One folder per feature
    auth/                State, Action, Reducer, ViewModel, Screen
  data/repository/       Repositories — interface + live + fake
  domain/model/          Domain models (User, etc.)
  di/                    Hilt modules
app/src/test/
  features/auth/         AuthReducerTest, AuthViewModelTest
```

## Build

```bash
./gradlew assembleDebug
```

## Test

```bash
./gradlew test
```

## Lint

```bash
./gradlew detekt
```

## Open in Android Studio

File → Open → select the repo root folder → Android Studio detects the Gradle project automatically.

## Pattern

See [ARCHITECTURE.md](https://github.com/applyfuse/fuse-docs/blob/main/ARCHITECTURE.md)
