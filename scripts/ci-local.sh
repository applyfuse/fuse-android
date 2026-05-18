#!/usr/bin/env bash
# FUSE: local CI mirror (Android).
#
# Runs the same three things .github/workflows/ci.yml runs on the
# GitHub ubuntu runner, so "passes locally" ≈ "passes on CI".
#
# Usage
# -----
# Once, on first checkout:
#     chmod +x scripts/ci-local.sh
#
# Then before every push (or whenever you want verification):
#     ./scripts/ci-local.sh
#
# If output volume is overwhelming and you only need failures:
#     ./scripts/ci-local.sh 2>&1 | tail -120
#
# Caveats (Android equivalents of lessons learned on fuse-ios)
# ------------------------------------------------------------
# 1. Keystore. JVM unit tests have no Android Keystore.
#    LiveTokenStore tests must probe-and-skip (Assumptions.assumeTrue)
#    if EncryptedSharedPreferences can't init — the analogue of iOS's
#    XCTSkip Keychain probe. They should SKIP locally, not fail.
#
# 2. Coroutine scheduling. Local and CI schedule coroutines
#    differently under load. Concurrency tests (single-flight
#    refresh) must be deterministic by STRUCTURE (runTest +
#    StandardTestDispatcher + advanceUntilIdle, or a gated
#    CompletableDeferred), never wall-clock delay(). iOS flaked on a
#    100ms sleep here — do not repeat.
#
# 3. Shared mutable test doubles. A Fake recording calls from
#    concurrent coroutines must lock its list (Mutex or
#    Collections.synchronizedList). iOS crashed locally on the bare-
#    list version while CI passed — different scheduler.
#
# 4. JDK / Gradle drift. CI pins JDK 17 + Gradle 8.9. A different
#    local JDK can change Detekt/Kotlin output. `java -version`
#    should report 17; if not, switch before trusting results.
#
# Exits non-zero on any failure. Safe in a pre-push hook.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# Prefer the gradle wrapper if present; otherwise fall back to a
# system gradle (CI uses gradle/actions/setup-gradle with 8.9).
if [ -x "./gradlew" ]; then
    GRADLE="./gradlew"
else
    GRADLE="gradle"
fi

echo "▶ java -version (CI pins JDK 17)"
java -version

echo ""
echo "▶ $GRADLE test"
$GRADLE test --stacktrace

echo ""
echo "▶ $GRADLE detekt"
$GRADLE detekt

echo ""
echo "▶ $GRADLE assembleDebug"
$GRADLE assembleDebug

echo ""
echo "✓ All local CI checks passed."
echo "  Test report:  app/build/reports/tests/"
echo "  Detekt report: app/build/reports/detekt/"
