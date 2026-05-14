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
core/              BaseViewModel, AppDispatchers, AppError
features/          One folder per feature
data/              Repositories — interface + live + fake
di/                Hilt modules
```

## Run

Open in Android Studio → Run → Select emulator or device

## Test

```bash
./gradlew test
```

## Lint

```bash
./gradlew detekt
```

## Pattern

See [ARCHITECTURE.md](https://github.com/applyfuse/fuse-docs/blob/main/ARCHITECTURE.md)
