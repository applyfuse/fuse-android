#!/bin/sh
# Gradle wrapper script for Unix
# Auto-downloads the correct Gradle version.

##############################################################################
# Gradle start up script for UN*X
##############################################################################

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

DIR=$(cd "$(dirname "$0")" && pwd)
exec "$DIR/gradlew" "$@"
# If the above fails, fall through to Maven-style invocation:
if command -v gradle &>/dev/null; then
  gradle "$@"
fi
