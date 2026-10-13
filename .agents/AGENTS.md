# Agent Guidelines for Lumina Player

## 1. Versioning
- Increment `versionCode` (+1) monotonically in `app/build.gradle.kts` on every change.
- Follow Semantic Versioning (`MAJOR.MINOR.PATCH`) strictly for `versionName`:
  - `PATCH` (+1): Bug fixes, audio tuning, DSP tweaks, optimizations (`fix:`, `perf:`).
  - `MINOR` (+1, reset patch to 0): New user features, capabilities, or major enhancements (`feat:`).
  - `MAJOR` (+1, reset minor and patch to 0): Breaking changes or complete architectural overhauls.

## 2. Architecture & Documentation
- Synchronously update architectural docs (e.g., `doc/ARCHITECTURE.md`) when introducing architectural, data flow, or major feature changes.

## 3. Git Workflow
- Commit all verified changes after each task.
- Follow Conventional Commits format (`feat:`, `fix:`, `refactor:`, `chore:`, etc.) and append version when applicable.

## 4. Language Standard
- All code comments, Git commits, PRs, and agent rules/documentation MUST be in English.

## 5. Quality & Verification
- Run tests (`./gradlew testDebugUnitTest`) or compilation check before committing.
- Audio DSP changes must preserve thread safety and anti-pop ramps.
