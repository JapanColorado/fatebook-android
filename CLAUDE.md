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
- `editQuestion` and `setSharedPublicly` use PATCH; `deleteQuestion` uses DELETE — Retrofit `@HTTP` annotation handles these (not `@FormUrlEncoded @POST`)
- `getQuestion` returns a single `QuestionDto` (not wrapped in an envelope) — used for deep links and enriching the detail sheet with comments

## Testing

**Unit tests** (JUnit + MockK + Turbine + Truth): Cover domain models, `QuestionRepository`, all ViewModels, and `ApiKeyInterceptor`. Run on JVM, no emulator needed.

**Screenshot tests** (Paparazzi): Render Compose UI to PNG on JVM using Android Studio's layoutlib. Golden images committed to `app/src/test/snapshots/images/`. Claude can read these PNGs to visually inspect the UI without a device.

**Screen composable pattern**: Each screen has a connected wrapper (calls `hiltViewModel()`) and a stateless `*Content` composable (takes state + callbacks). The `*Content` versions are what Paparazzi renders. Example: `FeedScreen` → `FeedScreenContent`.

**CI**: `.github/workflows/ci.yml` runs three parallel jobs on every PR: lint, unit tests, screenshot verification.

**Test fakes** (in `app/src/test/.../testutil/`):
- `FakeFatebookApi` — implements `FatebookApi` interface, records calls
- `FakeQuestionDao` — in-memory DAO using `MutableStateFlow`
- `FakeForecastDao` — in-memory forecast DAO
- `UserPreferences` is mocked with MockK (concrete class with Android deps)

## Package Structure

```
dev.russell.fatebook/
├── data/remote/         # Retrofit API interface, interceptor, DTOs
├── data/local/          # Room database, DAOs (QuestionDao, ForecastDao), entities
├── data/preferences/    # EncryptedSharedPrefs + DataStore
├── data/repository/     # QuestionRepository (offline-first)
├── domain/model/        # Question, Forecast, Comment, Resolution
├── ui/theme/            # Material3 theme, colors, typography
├── ui/components/       # QuestionCard, ProbabilitySlider, DatePickerField, ShimmerQuestionCard, ErrorBanner
├── ui/feed/             # FeedScreen + FeedScreenContent + FeedViewModel
├── ui/create/           # CreateScreen + CreateScreenContent + CreateViewModel
├── ui/analytics/        # AnalyticsScreen + AnalyticsScreenContent + AnalyticsViewModel
├── ui/resolve/          # Resolve bottom sheet
├── ui/detail/           # Question detail bottom sheet
├── ui/settings/         # SettingsScreen + SettingsScreenContent + SettingsViewModel
├── notification/        # WorkManager reminder (ReminderWorker, Scheduler, Helper)
├── navigation/          # Routes, NavGraph
└── di/                  # Hilt modules (Network, Database, Repository)
```

## What's Implemented

- **Settings screen**: API key input (obscured), validation against real API, "Get key" link, notification time picker (clock dialog), Android 13+ notification permission request
- **Question feed**: LazyColumn with filter chips (Active / Ready to Resolve / Resolved), pull-to-refresh, search bar, empty states, shimmer skeleton loading, cursor-based pagination with infinite scroll
- **Quick create**: Title field (auto-capitalized), date picker (default: tomorrow), probability slider with quick-set chips (10/25/50/75/90%)
- **Resolve flow**: Bottom sheet with YES (green) / NO (red) / Ambiguous buttons, loading indicator, error surfacing via error banner. Resolve state lives in `FeedViewModel`.
- **Question detail**: Tapping non-resolvable cards opens a detail bottom sheet (title, dates, notes, forecast, resolution, comments, "Open in Fatebook" link). Active questions show ProbabilitySlider + "Update Forecast" button. Action row provides Edit (pencil), Delete (trash with confirmation dialog), Share (Android share intent), and Visibility toggle (public/private via API). Edit mode replaces read-only fields with editable TextFields + DatePicker. Comments section shows existing comments and allows adding new ones via API.
- **Deep links**: Intent filter for `https://fatebook.io/q/*` URLs. Parses question slug from URL, fetches via `getQuestion` API, and opens the detail sheet. Handles both cold-start and in-app navigation via `onNewIntent`.
- **hideForecastsUntil**: Forecasts with a future `hideForecastsUntil` date show "Hidden" in the card and "Forecast hidden until [date]" in the detail sheet
- **Smart notification**: WorkManager daily task, only fires if no prediction made today. Tapping notification navigates to Create screen. Both `createQuestion` and `addForecast` update the last prediction date.
- **Error handling**: Network errors (IOException) show a persistent `ErrorBanner` with retry button; other errors also use the banner with dismiss option.
- **Analytics screen**: Accessible via chart icon in feed TopAppBar. Shows Brier score (with help popup), calibration chart (selectable dots, 5% buckets), prediction streak tracker, and weekly activity bar chart (clickable bars). Uses ALL forecasts per question (stored in `ForecastEntity` table), not just the latest — matching the Fatebook website. `AnalyticsViewModel.init` calls `loadAllQuestions()` to fetch all pages before computing.
- **Multi-option filtering**: Only BINARY questions shown; MULTIPLE_CHOICE and QUANTITY types are filtered out in the repository.
- **Offline-first**: Room cache shows questions immediately, background API refresh
- **Material You**: Dynamic color theming on Android 12+, dark mode support
- **Navigation gate**: First launch → Settings; after API key → Feed
- **ProGuard/R8**: Release builds use minification, resource shrinking, and targeted keep rules
