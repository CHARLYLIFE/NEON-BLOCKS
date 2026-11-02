
#!/usr/bin/env bash
set -euo pipefail

# Always use the project's pinned Gradle version.
# Never use the runner's preinstalled Gradle.
GRADLE_VERSION="8.13"

USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
BASE="$USER_HOME/neon-bootstrap"
INSTALL="$BASE/gradle-$GRADLE_VERSION"
ZIP="$BASE/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$INSTALL/bin/gradle" ]; then
    mkdir -p "$BASE"

    URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

    if [ ! -s "$ZIP" ]; then
        echo "Downloading Gradle $GRADLE_VERSION..."

        if command -v curl >/dev/null 2>&1; then
            curl -fL --retry 3 --retry-delay 2 "$URL" -o "$ZIP"
        elif command -v wget >/dev/null 2>&1; then
            wget "$URL" -O "$ZIP"
        else
            echo "ERROR: curl or wget is required."
            exit 1
        fi
    fi

    if ! command -v unzip >/dev/null 2>&1; then
        echo "ERROR: unzip is not installed."
        exit 1
    fi

    if ! unzip -tq "$ZIP" >/dev/null; then
        echo "ERROR: Gradle distribution ZIP is invalid."
        rm -f "$ZIP"
        exit 1
    fi

    echo "Installing Gradle $GRADLE_VERSION..."
    unzip -q -o "$ZIP" -d "$BASE"
fi

echo "Using pinned Gradle $GRADLE_VERSION"

exec "$INSTALL/bin/gradle" "$@"
