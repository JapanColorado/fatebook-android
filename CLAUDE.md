# Fatebook Android App

Unofficial personal Android client for [fatebook.io](https://fatebook.io) prediction tracking.
Package: `dev.russell.fatebook`

## Build & Deploy

All commands go through pixi (which provides the JDK — the system only has a JRE):

```bash
pixi run build                  # ./gradlew assembleDebug
pixi run deploy                 # build + adb install
pixi run clean                  # ./gradlew clean
pixi run test                   # ./gradlew test (all JVM tests)
pixi run test-unit              # ./gradlew testDebugUnitTest
pixi run test-screenshot-record # ./gradlew recordPaparazziDebug (regenerate goldens)
pixi run test-screenshot-verify # ./gradlew verifyPaparazziDebug (compare vs goldens)
pixi run test-all               # unit tests + screenshot verify
pixi run lint                   # ./gradlew lintDebug
```

Do NOT run `./gradlew` directly — it will fail because the system JDK is incomplete. Always use `pixi run`.

Android SDK location: `~/Android/Sdk`

## Architecture

MVVM + Repository pattern, offline-first.

```
Fatebook REST API → FatebookApi (Retrofit) → QuestionRepository → Room DB
                                                                 ↕
                                                              ViewModels → Compose UI
```

- **Single source of truth**: `QuestionRepository` orchestrates API ↔ Room cache
- **API key**: Stored in `EncryptedSharedPreferences` (hardware-backed on Pixel)
- **Notification prefs**: Stored in Jetpack DataStore
- **DI**: Hilt (modules in `di/`)
- **Navigation**: Compose Navigation with string routes

## Key API Quirks

- `createQuestion` is a **GET** request (not POST) — returns plain-text URL, not JSON
- `apiKey` is a **query parameter** for GET requests (handled by `ApiKeyInterceptor`), but must be an explicit **`@Field`** in POST request bodies (`resolveQuestion`, `addForecast`)
- API dates default to midnight UTC — `QuestionRepository.parseInstant()` handles both ISO 8601 and YYYY-MM-DD
- Scalars converter must be registered before Moshi in Retrofit (see `NetworkModule`)

## Testing

**Unit tests** (JUnit + MockK + Turbine + Truth): Cover domain models, `QuestionRepository`, all ViewModels, and `ApiKeyInterceptor`. Run on JVM, no emulator needed.

**Screenshot tests** (Paparazzi): Render Compose UI to PNG on JVM using Android Studio's layoutlib. Golden images committed to `app/src/test/snapshots/images/`. Claude can read these PNGs to visually inspect the UI without a device.

**Screen composable pattern**: Each screen has a connected wrapper (calls `hiltViewModel()`) and a stateless `*Content` composable (takes state + callbacks). The `*Content` versions are what Paparazzi renders. Example: `FeedScreen` → `FeedScreenContent`.

**CI**: `.github/workflows/ci.yml` runs three parallel jobs on every PR: lint, unit tests, screenshot verification.

**Test fakes** (in `app/src/test/.../testutil/`):
- `FakeFatebookApi` — implements `FatebookApi` interface, records calls
- `FakeQuestionDao` — in-memory DAO using `MutableStateFlow`
- `UserPreferences` is mocked with MockK (concrete class with Android deps)

## Package Structure

```
dev.russell.fatebook/
├── data/remote/         # Retrofit API interface, interceptor, DTOs
├── data/local/          # Room database, DAO, entity
├── data/preferences/    # EncryptedSharedPrefs + DataStore
├── data/repository/     # QuestionRepository (offline-first)
├── domain/model/        # Question, Forecast, Resolution
├── ui/theme/            # Material3 theme, colors, typography
├── ui/components/       # QuestionCard, ProbabilitySlider, DatePickerField, ShimmerQuestionCard
├── ui/feed/             # FeedScreen + FeedScreenContent + FeedViewModel
├── ui/create/           # CreateScreen + CreateScreenContent + CreateViewModel
├── ui/resolve/          # Resolve bottom sheet
├── ui/detail/           # Question detail bottom sheet
├── ui/settings/         # SettingsScreen + SettingsScreenContent + SettingsViewModel
├── notification/        # WorkManager reminder (ReminderWorker, Scheduler, Helper)
├── navigation/          # Routes, NavGraph
└── di/                  # Hilt modules (Network, Database, Repository)
```

## v1 — What's Implemented

- **Settings screen**: API key input (obscured), validation against real API, "Get key" link, notification time picker (clock dialog), Android 13+ notification permission request
- **Question feed**: LazyColumn with filter chips (Active / Ready to Resolve / Resolved), pull-to-refresh, search bar, empty states, shimmer skeleton loading, cursor-based pagination with infinite scroll
- **Quick create**: Title field (auto-capitalized), date picker (default: tomorrow), probability slider with quick-set chips (10/25/50/75/90%)
- **Resolve flow**: Bottom sheet with YES (green) / NO (red) / Ambiguous buttons, loading indicator, error surfacing via snackbar. Resolve state lives in `FeedViewModel`.
- **Question detail**: Tapping non-resolvable cards opens a detail bottom sheet (title, dates, forecast, resolution, "Open in Fatebook" link). Active questions show ProbabilitySlider + "Update Forecast" button to add a new forecast.
- **hideForecastsUntil**: Forecasts with a future `hideForecastsUntil` date show "Hidden" in the card and "Forecast hidden until [date]" in the detail sheet
- **Smart notification**: WorkManager daily task, only fires if no prediction made today
- **Offline-first**: Room cache shows questions immediately, background API refresh
- **Material You**: Dynamic color theming on Android 12+, dark mode support
- **Navigation gate**: First launch → Settings; after API key → Feed
