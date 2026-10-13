# Agent Guidelines for Lumina Player

## 1. Versioning
- Increment `versionCode` (+1) and update `versionName` in `app/build.gradle.kts` on every functional change.

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
