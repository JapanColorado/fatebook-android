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

Android SDK location: `/usr/lib/android-sdk`

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
- **Multi-option filtering**: The API returns `type` field (`BINARY`, `MULTIPLE_CHOICE`, `QUANTITY`). Only `BINARY` questions are supported; others are filtered out in `QuestionRepository.refresh()`/`loadMore()`.

## Key API Quirks

- `createQuestion` is a **GET** request (not POST) — returns plain-text URL, not JSON
- `apiKey` is a **query parameter** for GET requests (handled by `ApiKeyInterceptor`), but must be an explicit **`@Field`** in POST request bodies (`resolveQuestion`, `addForecast`)
- API dates default to midnight UTC — `QuestionRepository.parseInstant()` handles both ISO 8601 and YYYY-MM-DD. Resolve-by dates are conceptually **dates** (not timestamps): `Question.resolveByDate` extracts `LocalDate` via UTC. All date comparisons (ready-to-resolve, overdue, display) use this property and `LocalDate.now()` (device timezone)
- Scalars converter must be registered before Moshi in Retrofit (see `NetworkModule`)
- The API returns `type` for question type but the resolve endpoint expects `questionType` as input — these are **different field names** for the same concept
- `editQuestion` and `setSharedPublicly` use PATCH (form-encoded body); `deleteQuestion` uses DELETE with **query params** (no body, like GET) — `ApiKeyInterceptor` adds the key automatically
- `getQuestions` (list endpoint) **does** return comments with nested `user` objects — these are stored in Room's `CommentEntity` table during refresh

## Testing

**Unit tests** (JUnit + MockK + Turbine + Truth): Cover domain models, `QuestionRepository`, all ViewModels, and `ApiKeyInterceptor`. Run on JVM, no emulator needed.

**Screenshot tests** (Paparazzi): Render Compose UI to PNG on JVM using Android Studio's layoutlib. Golden images committed to `app/src/test/snapshots/images/`. Claude can read these PNGs to visually inspect the UI without a device.

**Screen composable pattern**: Each screen has a connected wrapper (calls `hiltViewModel()`) and a stateless `*Content` composable (takes state + callbacks). The `*Content` versions are what Paparazzi renders. Example: `FeedScreen` → `FeedScreenContent`.

**CI**: `.github/workflows/ci.yml` runs four parallel jobs on every PR: lint, unit tests, screenshot verification, and release APK build.

**Test fakes** (in `app/src/test/.../testutil/`):
- `FakeFatebookApi` — implements `FatebookApi` interface, records calls
- `FakeQuestionDao` — in-memory DAO using `MutableStateFlow`
- `FakeForecastDao` — in-memory forecast DAO
- `FakeCommentDao` — in-memory comment DAO
- `UserPreferences` is mocked with MockK (concrete class with Android deps)

## Package Structure

```
dev.russell.fatebook/
├── data/remote/         # Retrofit API interface, interceptor, DTOs
├── data/local/          # Room database, DAOs (QuestionDao, ForecastDao, CommentDao), entities
├── data/preferences/    # EncryptedSharedPrefs + DataStore
├── data/repository/     # QuestionRepository (offline-first)
├── domain/model/        # Question, Forecast, Comment, Resolution
├── ui/theme/            # Material3 theme, colors, typography
├── ui/components/       # QuestionCard, ProbabilitySlider, DatePickerField, ShimmerQuestionCard, ErrorBanner
├── ui/feed/             # FeedScreen + FeedScreenContent + FeedViewModel
├── ui/create/           # CreateScreen + CreateScreenContent + CreateViewModel
├── ui/analytics/        # AnalyticsScreen + AnalyticsScreenContent + AnalyticsViewModel
├── ui/detail/           # Question detail bottom sheet (includes resolve flow)
├── ui/settings/         # SettingsScreen + SettingsScreenContent + SettingsViewModel
├── notification/        # WorkManager reminder (ReminderWorker, Scheduler, Helper)
├── navigation/          # Routes, NavGraph
└── di/                  # Hilt modules (Network, Database, Repository)
```

## What's Implemented

- **Settings screen**: API key input (obscured), validation against real API, "Get key" link, notification time picker (clock dialog), Android 13+ notification permission request, inline privacy summary + "Privacy Policy" link (GitHub Pages at `japancolorado.github.io/FatebookApp/privacy-policy`)
- **Question feed**: LazyColumn with filter chips (Active / Ready to Resolve / Resolved), pull-to-refresh, search bar, empty states, shimmer skeleton loading, cursor-based pagination with infinite scroll
- **Quick create**: Title field (auto-capitalized), date picker (default: tomorrow), probability slider with quick-set chips (10/25/50/75/90%)
- **Question detail & resolve**: Tapping any card opens a unified detail bottom sheet (title, dates, notes, forecast, resolution, comments). Ready-to-resolve questions show YES/NO/Ambiguous buttons; active questions show ProbabilitySlider + "Update Forecast" button. Action row provides Edit (pencil), Delete (trash with confirmation dialog), Share (Android share intent), Open in Fatebook (external link), and Visibility toggle (eye icon with "Public"/"Private" label). Edit mode replaces read-only fields with editable TextFields + DatePicker. Comments section shows existing comments (with author name and date) and allows adding new ones. Comments are persisted in Room's `CommentEntity` table, synced from the `getQuestions` list API.
- **hideForecastsUntil**: Forecasts with a future `hideForecastsUntil` date show "Hidden" in the card and "Forecast hidden until [date]" in the detail sheet
- **Notifications**: Two separate notification channels, both fired by a single daily WorkManager task at the user's chosen time:
  - *Daily Reminder* (`daily_reminder` channel): Always fires, tapping opens Create screen.
  - *Ready to Resolve* (`ready_to_resolve` channel): Fires only when there are unresolved questions past their resolve-by date, showing the count. Tapping opens Feed with the "Ready to Resolve" filter pre-selected.
  - Separate channels allow users to independently mute each in Android settings. Separate notification IDs (1, 2) and PendingIntent request codes (0, 1) prevent collisions.
- **Error handling**: `FeedError` sealed interface classifies errors: `Network` (IOException), `Auth` (HTTP 401/403 — shows "Settings" button instead of "Retry"), `RateLimited` (HTTP 429), and `Other`. `ErrorBanner` supports custom action labels via `actionLabel`/`onAction` params. Retry uses exponential backoff (1s-16s cap). HTTP logging is conditional — `Level.BODY` in debug, `Level.NONE` in release (requires `buildConfig = true` in `build.gradle.kts`).
- **Analytics screen**: Accessible via chart icon in feed TopAppBar. Shows Brier score (with help popup), calibration chart (selectable dots, 5% buckets), prediction streak tracker, and weekly activity bar chart (clickable bars). Uses ALL forecasts per question (stored in `ForecastEntity` table), not just the latest — matching the Fatebook website. `AnalyticsViewModel.init` calls `loadAllQuestions()` to fetch all pages before computing.
- **Multi-option filtering**: Only BINARY questions shown; MULTIPLE_CHOICE and QUANTITY types are filtered out in the repository.
- **Offline-first**: Room cache shows questions immediately, background API refresh
- **Material You**: Dynamic color theming on Android 12+, dark mode support
- **Navigation gate**: First launch → Settings; after API key → Feed
- **ProGuard/R8**: Release builds use minification, resource shrinking, and targeted keep rules. Release buildType uses debug signing config as fallback for CI builds.
- **Accessibility**: ProbabilitySlider has semantics for screen readers. Calibration and activity charts provide text summaries via `Modifier.semantics`. Feed empty state on Active filter shows "No predictions yet / Tap + to create your first prediction".
