name: Android build

on:
  push:
    branches: [ main, master ]
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - name: Set up Android SDK
        uses: android-actions/setup-android@v3
      - name: Install Android 36 platform and build tools
        run: |
          yes | sdkmanager --licenses >/dev/null
          sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
      - name: Set up Gradle 8.13
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.13'
      - name: Unit tests and debug APK
        run: gradle --no-daemon testDebugUnitTest assembleDebug
      - name: Upload debug APK
        uses: actions/upload-artifact@v4
        with:
          name: neon-blocks-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error
      - name: Configure release signing (optional)
        env:
          KEYSTORE_B64: ${{ secrets.RELEASE_KEYSTORE_BASE64 }}
          STORE_PASS: ${{ secrets.RELEASE_STORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.RELEASE_KEY_ALIAS }}
          KEY_PASS: ${{ secrets.RELEASE_KEY_PASSWORD }}
        run: |
          if [ -n "${KEYSTORE_B64:-}" ] && [ -n "${STORE_PASS:-}" ] && [ -n "${KEY_ALIAS:-}" ] && [ -n "${KEY_PASS:-}" ]; then
            echo "$KEYSTORE_B64" | base64 --decode > "$RUNNER_TEMP/neon-release.jks"
            {
              printf 'RELEASE_STORE_FILE=%s\n' "$RUNNER_TEMP/neon-release.jks"
              printf 'RELEASE_STORE_PASSWORD=%s\n' "$STORE_PASS"
              printf 'RELEASE_KEY_ALIAS=%s\n' "$KEY_ALIAS"
              printf 'RELEASE_KEY_PASSWORD=%s\n' "$KEY_PASS"
            } >> "$GITHUB_ENV"
          else
            echo "Signing secrets are absent or incomplete; the AAB artifact will be unsigned."
          fi
      - name: Build release AAB (signed when secrets are configured)
        run: gradle --no-daemon bundleRelease
      - name: Upload release AAB
        uses: actions/upload-artifact@v4
        with:
          name: neon-blocks-release-aab
          path: app/build/outputs/bundle/release/app-release.aab
          if-no-files-found: error
