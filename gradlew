#!/usr/bin/env bash
set -euo pipefail
GRADLE_VERSION="8.13"
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/neon-bootstrap"
INSTALL="$BASE/gradle-$GRADLE_VERSION"
if [ ! -x "$INSTALL/bin/gradle" ]; then
  mkdir -p "$BASE"
  ZIP="$BASE/gradle-$GRADLE_VERSION-bin.zip"
  echo "Downloading Gradle $GRADLE_VERSION..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP"
  else
    wget "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O "$ZIP"
  fi
  unzip -q -o "$ZIP" -d "$BASE"
fi
exec "$INSTALL/bin/gradle" "$@"
