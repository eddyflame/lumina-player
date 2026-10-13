# Contributing to Lumina Player

Thank you for your interest in contributing to **Lumina Player**!

Lumina Player is a minimalist, modern, audiophile-grade local music player built with 100% Kotlin, Jetpack Compose, and AndroidX Media3. We are committed to zero ads, zero telemetry, and 100% offline privacy.

Please take a moment to review these guidelines before submitting issues or pull requests.

---

## 🛠️ Development Setup

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1) or newer
- **JDK**: Java 17 or Java 21 (Temurin / OpenJDK)
- **Android SDK**: API 36 (Compile SDK 36, Min SDK 26)
- **Gradle**: 9.6.0 (configured via `./gradlew` wrapper)

### Getting the Code
```bash
git clone https://github.com/eddyflame/lumina-player.git
cd lumina-player
```

### Building & Testing via CLI
```bash
# Build Debug APK
./gradlew assembleDebug

# Run Unit Tests
./gradlew testDebugUnitTest

# Build Release APK
./gradlew assembleRelease
```

---

## 🌿 Branching Strategy

- **`main`**: Production/stable release branch. Tags (`v*`) are cut from here.
- **`develop`**: Integration branch for current active development.
- **Feature & Fix branches**:
  - `feature/<feature-name>` (branch off `develop`)
  - `fix/<bug-name>` (branch off `develop`)
  - `hotfix/<name>` (branch off `main` if critical)

Pull requests should target `develop` (or `main` for release merges).

---

## 📝 Commit Guidelines

We strictly follow [Conventional Commits](https://www.conventionalcommits.org/) and English language standards:

### Format
```
<type>: <short summary in English> [(vX.Y.Z)]

[optional body explaining context or rationale]
```

### Allowed Types
- `feat:` New user-facing feature or major enhancement.
- `fix:` Bug fix, audio tuning, DSP glitch fix.
- `perf:` Performance optimization (memory, startup time, frame rendering).
- `refactor:` Code restructuring without changing external behavior.
- `docs:` Documentation or README updates.
- `test:` Adding or updating unit tests.
- `chore:` Build scripts, dependencies, CI workflows, tooling.

### Language Standard
- **All commit messages, code comments, PR descriptions, and agent rules MUST be written in English.**

---

## 🔢 Versioning Guidelines

Every change must respect the versioning policy defined in `app/build.gradle.kts`:

1. **`versionCode`**: Must be incremented monotonically (`+1`) on every change.
2. **`versionName`**: Follows strict Semantic Versioning (`MAJOR.MINOR.PATCH`):
   - **PATCH** (`+1`): Bug fixes, audio tuning, DSP tweaks, optimizations (`fix:`, `perf:`).
   - **MINOR** (`+1`, reset patch to 0): New user features, capabilities, or major enhancements (`feat:`).
   - **MAJOR** (`+1`, reset minor and patch to 0): Breaking changes or complete architectural overhauls.

---

## 🎧 Audio DSP & Architecture Precautions

When modifying audio pipeline components:
1. **Thread Safety**: Never block the audio playback thread. State synchronization between `LuminaAudioService`, `PlayerController`, and UI must use Kotlin `StateFlow` and coroutines on appropriate dispatchers (`Dispatchers.IO` or `Dispatchers.Default`).
2. **Anti-Pop Envelopes**: Any seek, play, pause, or track transition must maintain soft volume ramps (e.g. raised-cosine / Hann window envelope) to prevent DAC capacitor charging spikes and audible clicks.
3. **Format Integrity**: Verify compatibility with high-resolution formats (24/32-bit FLAC, DSD, APE, CUE virtual tracks).
4. **Documentation**: If data flows, architecture, or storage schemas are modified, synchronously update [`doc/ARCHITECTURE.md`](doc/ARCHITECTURE.md).

---

## 🚀 Submitting a Pull Request

1. Fork the repository and create your branch from `develop`.
2. Ensure `./gradlew testDebugUnitTest` passes locally.
3. Verify that your change compiles cleanly with `./gradlew assembleDebug`.
4. Follow the [Pull Request Template](.github/PULL_REQUEST_TEMPLATE.md).
5. Submit your PR against `develop` and wait for the automated GitHub Actions CI checks to run.
