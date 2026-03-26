# Fatebook for Android

An unofficial Android client for [Fatebook](https://fatebook.io), the prediction-tracking platform. Built to make daily prediction logging frictionless on mobile.

## Features

- **Quick create** — title, resolve date, probability slider with quick-set chips
- **Question feed** — filterable list (active / ready to resolve / resolved) with pull-to-refresh and text search
- **Resolve flow** — bottom sheet with YES / NO / Ambiguous buttons and loading state
- **Question detail** — tap any card to see details, forecast date, resolution, and link to fatebook.io
- **Smart daily reminder** — notification fires only if you haven't made a prediction today
- **Offline-first** — cached questions show immediately, syncs in background
- **Material You** — dynamic color theming on Android 12+

## Setup

### Prerequisites

- [pixi](https://pixi.sh) package manager
- Android SDK (installed at `~/Android/Sdk`, or update `local.properties`)
- Android phone with USB debugging enabled

### Build & Install

```bash
pixi install           # Install JDK + Gradle via pixi
pixi run build         # Build debug APK
pixi run deploy        # Build + install to connected device
```

### API Key

1. Go to [fatebook.io/api-setup](https://fatebook.io/api-setup)
2. Copy your API key
3. Open the app → Settings → paste the key → Save & Connect

## Tech Stack

Kotlin, Jetpack Compose, Hilt, Retrofit + Moshi, Room, WorkManager, EncryptedSharedPreferences

## License

Personal project. Not affiliated with Fatebook/Sage.
