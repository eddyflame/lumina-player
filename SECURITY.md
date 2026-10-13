# Security Policy

## Supported Versions

We actively support and provide security updates for the following versions of Lumina Player:

| Version | Supported          |
| ------- | ------------------ |
| 0.6.x   | :white_check_mark: |
| < 0.6.0 | :x:                |

---

## 🔒 Privacy & Architecture Principles

Lumina Player is built from the ground up as a **100% offline, privacy-first audio player**:
- **Zero Internet Permissions**: `android.permission.INTERNET` is **not requested** in `AndroidManifest.xml`. The application cannot initiate network connections or transmit data externally.
- **Zero Telemetry & Ads**: The codebase contains no tracking, user behavior analytics, crash reporting daemons, or advertisement SDKs.
- **Strictly Local Storage**: All music indexing, playlist data, playback statistics, and user preferences are retained solely inside the device's sandboxed SQLite database and Android Preferences.

---

## 🛡️ Reporting a Vulnerability

If you discover a security vulnerability or privacy concern within Lumina Player:

1. **Do not disclose the issue publicly** in GitHub Issues or Discussions.
2. Please report the issue via GitHub [Private Vulnerability Reporting](https://github.com/your-username/lumina-player/security/advisories/new) or send an email directly to the maintainer at `tp_eflame@163.com`.
3. Include detailed steps to reproduce the issue, sample audio files if applicable, and device/OS environment details.
4. We will acknowledge receipt of your report within 48 hours and work with you to validate and remediate the issue promptly.
