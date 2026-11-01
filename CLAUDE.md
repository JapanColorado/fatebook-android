# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Fatebook (`dev.russell.fatebook`): an unofficial personal Android client for [fatebook.io](https://fatebook.io) prediction tracking.

## Commands

All commands go through pixi (which provides the JDK — the system only has a JRE):

```bash
pixi run build                  # ./gradlew assembleRelease (day-to-day build)
pixi run install                # release build + install on the connected device (the default install)
pixi run run                    # install + launch
pixi run build-debug            # ./gradlew assembleDebug (appId dev.russell.fatebook.debug, installs alongside release)
pixi run install-debug          # debug build + install (debugger/layout-inspector sessions)
pixi run run-debug              # debug install + launch
pixi run clean                  # ./gradlew clean
pixi run test                   # ./gradlew testDebugUnitTest
pixi run test-screenshot-record # ./gradlew recordPaparazziDebug (regenerate goldens)
pixi run test-screenshot-verify # ./gradlew verifyPaparazziDebug (compare vs goldens)
pixi run test-all               # unit tests + screenshot verify
pixi run lint                   # ./gradlew lintDebug
pixi run logs                   # logcat for the running release app
```

Release builds are signed from a gitignored `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) if present, else with the debug key. `app/src/debug/res/xml/shortcuts.xml` duplicates the main shortcuts with the `.debug` targetPackage; keep the two in sync.

Do NOT run `./gradlew` directly — it will fail because the system JDK is incomplete. Always use `pixi run`.

Android SDK location: `~/Android/Sdk` (user-writable; Gradle auto-downloads missing packages — the Debian SDK at `/usr/lib/android-sdk` lacks platforms and is not used)

## Architecture

MVVM + Repository pattern, offline-first.

```
Fatebook REST API → FatebookApi (Retrofit) → QuestionRepository → Room DB
                                                                 ↕
                                                              ViewModels → Compose UI
```

- **Single source of truth**: `QuestionRepository` orchestrates API ↔ Room cache
- **Set-diff refresh**: `QuestionRepository.refresh()` upserts response items and deletes questions not in the response, all inside one Room transaction (`Transactor` abstraction wraps `RoomDatabase.withTransaction`). FK CASCADE on `ForecastEntity`/`CommentEntity`/`OptionEntity` cleans up children. Locally-created questions (id prefixed `local-`) are kept across refresh.
- **Full-history mode**: after Analytics' one-time "Sync full history" completes, a DataStore flag (`fullHistorySynced`) makes `refresh()` delegate to `loadAllQuestions()` (all pages, then prune) — a page-1-only refresh would prune the synced history. Cost: one request per 100 questions per refresh.
- **Optimistic offline writes**: All seven mutations (`createQuestion`, `addForecast`, `resolveQuestion`, `editQuestion`, `deleteQuestion`, `setSharedPublicly`, `addComment`) apply to Room immediately AND insert a `PendingMutationEntity` row in the same transaction. The caller's coroutine never awaits the network. A `SyncWorker` (WorkManager, `NetworkType.CONNECTED` constraint) drains the queue when connectivity is available, with WorkManager-default exponential backoff on `IOException` and `Result.retry()`. After 5 HTTP-failure attempts on the same row, it's marked `ERRORED` and surfaced via the **Sync issues** banner.
- **Offline-created questions** use a temp id (`local-<uuid>`). When the queued `CREATE_QUESTION` syncs, `SyncRunner` refreshes the feed and matches the new server row by title + resolveBy + ±5min `createdAt` window. The temp PK is then rewritten via `questionDao.changeId()`; child `ForecastEntity`/`CommentEntity` rows follow automatically because their FKs declare `onUpdate = CASCADE`. Pending follow-up mutations on the temp id are rewritten via `pendingDao.rewriteQuestionId()` and an in-memory `tempId → realId` map handles the rest of the queue run.
- **Mutation collapsing**: deleting a `local-*` question whose `CREATE_QUESTION` hasn't synced yet drops both the local row and every queued mutation for it — the server never hears about it. No other collapsing; last-write-wins server-side handles repeated edits/toggles.
- **No refresh-after-mutation**: mutation handlers do targeted DAO updates locally; the only refresh during sync is the one inside `SyncRunner.syncCreate()` (needed to discover the server id).
- **API key**: Stored in `EncryptedSharedPreferences` (hardware-backed on Pixel). `ApiKeyConnector` is the single validate-and-persist path (used by Settings and Onboarding): it writes the candidate key, calls `validateApiKey()`, and **restores the previous key on failure** rather than wiping it. `UserPreferences.hasApiKeyFlow` mirrors the key reactively for the nav gate.
- **Current user id**: The public API never says who the key belongs to. `QuestionRepository.captureCurrentUser()` infers it once per key from the feed (the forecaster present on the most questions) and stores it in encrypted prefs (`userId`); the detail sheet uses it to pick "your" forecasts. A different key clears it.
- **Templates DB**: quick-forecast templates are user-authored, so they live in a separate `TemplateDatabase` (`templates.db`, v1). The question cache (`FatebookDatabase`) uses `fallbackToDestructiveMigration()` and is disposable; any schema change to `templates.db` needs a real `Migration`.
- **Notification prefs**: Stored in Jetpack DataStore
- **DI**: Hilt (modules in `di/`)
- **Navigation**: Compose Navigation with string routes. Bottom bar (`LedgerBottomBar`, hosted by `FatebookNavGraph`'s Scaffold) has three state-saving tabs, Ledger (`FEED`) / Record (`ANALYTICS`) / Settings, plus the New Prediction button; Create and Onboarding are full-screen. `FeedScreen` applies launch-intent targets once (`rememberSaveable`) because it leaves composition on every tab switch.
- **Design system ("Ledger")**: Fixed palette, no Material You: warm paper/ink in `ui/theme/Color.kt`, read non-Material colours through `Ledger.colors`. Ink is `primary`; the ochre `accent` means only "this is due". Probability uses the diverging `forecastColor` (coral ↔ neutral ↔ teal), and YES/NO reuse its ends, so never use red/green to imply a forecast is good or bad. Newsreader (prose), IBM Plex Mono (figures and uppercase labels), IBM Plex Sans (body) are bundled as static instances in `res/font`, licences in `assets/licenses`. Group content with rules (`LedgerRule`, `MastheadRule`, `LedgerSection` in `LedgerPrimitives.kt`), not cards.
- **Row quick actions**: Due rows (`DueRow`) resolve or snooze by swipe (binary: right YES / left NO; MC: left +1 week) or buttons, without opening the sheet. `FeedViewModel.quickAction` hides the row and shows `UndoStrip` for 5 seconds before writing anything, because the public API has no un-resolve. Leaving the feed or stopping the app flushes the pending action.
- **Question types**: The API returns `type` (`BINARY`, `MULTIPLE_CHOICE`, `QUANTITY`). All three are cached and shown. MC is fully interactive (per-option forecasts via `optionId`, resolve-to-option); QUANTITY is read-only (the public API can't act on it). MC options live in `OptionEntity` (FK CASCADE, server option ids). **Option-level forecasts appear in the question-level `forecasts` array with an `optionId`** — `options[].forecasts` is a duplicate view and is deliberately not parsed (would double-count).
- **MC resolution semantics** (mirrors server, see `resolveMultipleChoice`/`resolveOption`): exclusive questions resolve with the winning option's **text** (or `OTHER` → all options NO / parent NO, or `AMBIGUOUS`); non-exclusive questions resolve per option (`optionId` + YES/NO), and the parent resolves once every option is resolved. Local mirroring keys on option ids; only the payload carries text.
- **Tags**: denormalized `tagsJson` column on `QuestionEntity` (tags are read-only after creation — the public `editQuestion` has no tags field). Sent at create time as repeated `tags` query params.
- **Widget**: Glance widget reads Room via a Hilt EntryPoint; `SyncRunner` stays pure-Kotlin by depending on `fun interface WidgetRefresher` (no-op in tests). Updates on app open, after a successful sync drain, and on the daily reminder tick.

## Key API Quirks

- `createQuestion` is a **GET** request (not POST) — returns plain-text URL, not JSON. It MUST stay GET: the server parses `req.query || req.body` and Next.js `req.query` is always truthy, so a form POST 400s. Accepts repeated `tags` params but has **no `options` param** — multiple-choice questions cannot be created via API key (the tRPC `question.create` that accepts options requires browser session auth), so in-app creation stays binary-only.
- `addForecast`: `optionId` is **required** for MULTIPLE_CHOICE and **forbidden** (400) for BINARY. The server silently no-ops an identical forecast repeated within 2 minutes.
- `resolveQuestion`: binary resolutions are YES/NO/AMBIGUOUS; MC resolutions are the option's **text**, `OTHER`, or `AMBIGUOUS` (optionally `optionId` + YES/NO for one option of a non-exclusive question). `questionType` is required.
- `apiKey` is a **query parameter** for GET requests (handled by `ApiKeyInterceptor`), but must be an explicit **`@Field`** in POST request bodies (`resolveQuestion`, `addForecast`)
- API dates default to midnight UTC — `QuestionRepository.parseInstant()` handles both ISO 8601 and YYYY-MM-DD. Resolve-by dates are conceptually **dates** (not timestamps): `Question.resolveByDate` extracts `LocalDate` via UTC. All date comparisons (ready-to-resolve, overdue, display) use this property and `LocalDate.now()` (device timezone)
- Scalars converter must be registered before Moshi in Retrofit (see `NetworkModule`)
- The API returns `type` for question type but the resolve endpoint expects `questionType` as input — these are **different field names** for the same concept
- `editQuestion` and `setSharedPublicly` use PATCH (form-encoded body); `deleteQuestion` uses DELETE with **query params** (no body, like GET) — `ApiKeyInterceptor` adds the key automatically
- `getQuestions` (list endpoint) **does** return comments with nested `user` objects — these are stored in Room's `CommentEntity` table during refresh

## Testing

**Unit tests** (JUnit + MockK + Turbine + Truth): Cover domain models, `QuestionRepository`, all ViewModels, and `ApiKeyInterceptor`. Run on JVM, no emulator needed.

**Screenshot tests** (Paparazzi): Render Compose UI to PNG on JVM using Android Studio's layoutlib. Golden images committed to `app/src/test/snapshots/images/`. Claude can read these PNGs to visually inspect the UI without a device.

**Gesture tests** (`app/src/androidTest`, needs a device, not in CI): `pixi run ./gradlew connectedDebugAndroidTest` drives swipe-to-resolve and the forecast ruler with real pointer input.

**Screen composable pattern**: Each screen has a connected wrapper (calls `hiltViewModel()`) and a stateless `*Content` composable (takes state + callbacks). The `*Content` versions are what Paparazzi renders. Example: `FeedScreen` → `FeedScreenContent`.

**CI**: `.github/workflows/ci.yml` runs four parallel jobs on every PR: lint, unit tests, screenshot verification, and release APK build.

**Test fakes** (in `app/src/test/.../testutil/`):
- `FakeFatebookApi` — implements `FatebookApi` interface, records calls
- `FakeQuestionDao` — in-memory DAO using `MutableStateFlow`
- `FakeForecastDao` — in-memory forecast DAO
- `FakeCommentDao` — in-memory comment DAO
- `FakeOptionDao` — in-memory MC-option DAO
- `FakePendingMutationDao` — in-memory pending-mutation queue
- `FakeTemplateDao` — in-memory template store
- `UserPreferences` is mocked with MockK (concrete class with Android deps); stub `feedSort`/`fullHistorySynced`/`notificationToggles`/`lastPredictionDateEpochMs` flows or ViewModel/repository/worker pipelines stall
- `QuestionCard`, `FeedScreenContent` and `CreateScreenContent` take a `today: LocalDate` param (default `LocalDate.now()`) so goldens are deterministic — pass a fixed date in Paparazzi tests
- `Transactor` is the abstraction that wraps `RoomDatabase.withTransaction` in production; in tests, pass `Transactor { block -> block() }` to run the block inline.
- `SyncScheduler` is a `fun interface` over `SyncWorker.enqueue(context)` so tests can substitute a no-op or counting fake without standing up WorkManager.
- `SyncRunner` is the pure-Kotlin core of `SyncWorker`. Tests exercise it directly (`SyncRunnerTest`) — the worker class itself is a thin wrapper. Its `WidgetRefresher` param takes a counting lambda in tests.

## Package Structure

```
dev.russell.fatebook/
├── data/remote/         # Retrofit API interface, interceptor, DTOs
├── data/local/          # Room databases (FatebookDatabase cache, TemplateDatabase), DAOs (QuestionDao, ForecastDao, CommentDao, OptionDao, PendingMutationDao, TemplateDao), entities
├── data/preferences/    # EncryptedSharedPrefs + DataStore
├── data/repository/     # QuestionRepository (offline-first, optimistic writes), TemplateRepository, ApiKeyConnector
├── data/sync/           # SyncWorker, SyncRunner, MutationEnqueuer, payloads, SyncScheduler
├── data/network/        # NetworkMonitor (ConnectivityManager → StateFlow<Boolean>)
├── domain/model/        # Question, QuestionOption, QuestionType, Visibility, Forecast, Comment, Resolution, QuestionTemplate
├── ui/theme/            # Material3 theme, colors, typography
├── ui/components/       # LedgerPrimitives, QuestionCard, ProbabilitySlider, DatePickerField, ForecastSparkline (+ SparklineMath), ReminderTimeDialog, NotificationPermission, ShimmerQuestionCard, ErrorBanner, OfflineBanner, SyncIssuesBanner, SyncErrorsSheet
├── ui/onboarding/       # OnboardingScreen + OnboardingScreenContent + OnboardingViewModel (3-page pager)
├── ui/feed/             # FeedScreen + FeedScreenContent + FeedViewModel + QuestionSearch + DueRow
├── ui/create/           # CreateScreen + CreateScreenContent + CreateViewModel
├── ui/analytics/        # AnalyticsScreen + AnalyticsScreenContent + AnalyticsViewModel
├── ui/detail/           # Question detail bottom sheet (includes resolve flow)
├── ui/settings/         # SettingsScreen + SettingsScreenContent + SettingsViewModel
├── notification/        # Daily reminder (ReminderAlarmReceiver, ReminderTick, ReminderPolicy, Scheduler, Helper, ResolveActionReceiver)
├── widget/              # Glance home-screen widget (FatebookWidget, Receiver, WidgetRefresher)
├── navigation/          # Routes, NavGraph, LedgerBottomBar
└── di/                  # Hilt modules (Network, Database, Repository)
```

## What's Implemented

- **Onboarding**: Keyless launches start on a 3-page `HorizontalPager` (swiping disabled, driven by `OnboardingUiState.page`): Welcome → API key (open `fatebook.io/api-setup`, paste, Connect via `ApiKeyConnector`, auto-advances on success) → Notifications opt-in (switch + time picker + permission request; "Skip for now" / "Finish"). Finishing writes `onboarding_complete` and, if opted in, the reminder prefs + schedule. Shortcut/share intents that arrive keyless are honoured after onboarding.
- **Settings screen**: API key input (obscured), validation against real API, "Get key" link, master notifications switch + notification time picker (clock dialog, shared `ReminderTimeDialog`) + three per-kind toggles (daily reminder / ready to resolve / resolving soon), Android 13+ notification permission request (shared `rememberNotificationPermissionRequester`), inline privacy summary + "Privacy Policy" link (GitHub Pages at `japancolorado.github.io/FatebookApp/privacy-policy`)
- **Question feed**: Masthead (wordmark, date, due/open counts) over ruled rows, no cards. Text filter tabs (Open / Due with count / Soon [due in ≤7 days, `FeedFilter.RESOLVING_SOON_DAYS`] / Resolved), tag menu in the masthead, pull-to-refresh, collapsible search (matches title, notes, tags, MC option text **and comments** — comments come from `CommentDao.observeSearchIndex()`, only observed while a query is active; pure predicate in `QuestionSearch`), sort menu in the masthead (by resolve date / newest created, persisted in DataStore), empty states, shimmer skeleton loading, cursor-based pagination with infinite scroll
- **Quick create**: Title field (auto-capitalized), date picker (default: tomorrow), probability slider drawn as a draggable ruler with quick-set values (10/25/50/75/90%) under their ticks, a plain-language gloss, and −1/+1 bit-of-evidence keys flanking the big percent readout (each tap is 1 bit of log-odds: + doubles the odds, − halves them; pure math in `BitsMath`, clamped to 1–99%; part of the shared `ProbabilitySlider`, so they also appear in the detail sheet's binary and per-option forecast updates), free-text tag chips (sent with createQuestion), and a **Templates** chip row (tap fills the form with title/probability/tags and `today + resolveOffsetDays`; long-press deletes; bookmark icon in the TopAppBar saves the current form as a template), and one-tap resolve dates (tomorrow, 1 week, 1/3 months, 1 year). Entry points: bottom-bar + button, ACTION_SEND share target (shared text prefills the title via the `create?prefill=` route arg + SavedStateHandle), and static launcher shortcuts ("New prediction", "Ready to resolve")
- **Question detail & resolve**: Tapping any card opens a unified detail bottom sheet (title, dates, tags, notes, forecast, resolution, comments). Binary ready-to-resolve questions show YES/NO/Ambiguous buttons; active questions show ProbabilitySlider + "Update Forecast". Multiple-choice questions list options with per-option percentages. Active **exclusive** MC uses an interactive pie chart (`ProbabilityPieChart` + pure `PieChartMath`): dragging a handle on a slice boundary transfers probability between the two adjacent slices so options always sum to 100% (min slice 1%, the 12-o'clock boundary is fixed, values snap to whole percents); one "Update Forecasts" button submits `addForecast` per option whose whole-percent value changed. Active **non-exclusive** MC keeps per-option expanding sliders (independent probabilities). Ready-to-resolve MC shows resolve-to-option buttons (+ Other/Ambiguous) or per-option YES/NO for non-exclusive questions. Ready-to-resolve questions also get "+1 week"/"+1 month" push-the-date quick actions (edit from today). A "Your forecasts" section (binary questions, ≥2 of your forecasts, selected by `SparklineMath.yourForecasts` using the inferred user id — local/unsynced forecasts always count as yours) draws a step `ForecastSparkline` from `createdAt` to `resolvedAt ?: now` with rows underneath; a separate "Forecast history" section lists everyone's forecasts on shared questions (>1 distinct forecaster). Action row provides Edit (pencil, hidden for QUANTITY), Delete (trash with confirmation dialog), Share (Android share intent), Open in Fatebook (external link). A three-way Visibility segmented control sits at the bottom of the sheet (Private / Unlisted / Public → `sharedPublicly` + `unlisted`, `Question.visibility`). Comment input is multiline (4 lines max). Edit mode replaces read-only fields with editable TextFields + DatePicker. Comments are persisted in Room's `CommentEntity` table, synced from the `getQuestions` list API.
- **hideForecastsUntil**: Forecasts with a future `hideForecastsUntil` date show "Hidden" in the card and "Forecast hidden until [date]" in the detail sheet
- **Notifications**: Three notification channels, all fired by a single daily tick at the user's chosen time (device timezone), each behind its own toggle in Settings (`UserPreferences.notificationToggles`). The tick is an `AlarmManager` alarm, not WorkManager, whose periodic work has no time-of-day concept: it drifts, and its `UPDATE` policy ignores a changed time. `ReminderAlarmReceiver` re-arms for the next occurrence on each firing and after reboot, app update, clock/timezone change, or an exact-alarm grant; `ensureArmed()` also runs at app start. Exact timing needs the user-granted `SCHEDULE_EXACT_ALARM` (off by default on Android 14+, Settings shows a link to it); without it the alarm falls back to a 10-minute `setWindow` that Doze can delay:
  - *Daily Reminder* (`daily_reminder` channel): **Skipped on days a prediction was already made in-app** (`ReminderPolicy.predictedToday` reads `lastPredictionDateEpochMs`; website activity doesn't count). Tapping opens Create screen.
  - *Ready to Resolve* (`ready_to_resolve` channel): Per-question notifications (capped at 5, most-overdue first) grouped under a summary showing the total count. Binary questions carry YES/NO action buttons; every non-QUANTITY question carries a **+1 week** snooze action (`ACTION_SNOOZE` → `editQuestion(resolveBy = today + 1 week)`). All handled by `ResolveActionReceiver` (goAsync + offline mutation queue — works with the app dead); MC/QUANTITY notifications open the question's detail sheet via `EXTRA_QUESTION_ID`. Summary tap opens Feed with the "Ready to Resolve" filter.
  - *Resolving Soon* (`resolving_soon` channel): One inbox-style digest of questions due in the next `ReminderPolicy.RESOLVING_SOON_DAYS` (3) days; tap opens Feed on the "Resolving soon" filter (`EXTRA_OPEN_SOON_FILTER`).
  - Separate channels allow users to independently mute each in Android settings. Fixed IDs 1/2/3 for reminder/summary/digest; per-question IDs are stable hashes offset above 10000, and PendingIntent request codes are `1000 + hash*4 + action` (open/yes/no/snooze), clear of the fixed codes 0/1/2.
- **Error handling**: `FeedError` sealed interface classifies errors: `Network` (IOException), `Auth` (HTTP 401/403 — shows "Settings" button instead of "Retry"), `RateLimited` (HTTP 429), and `Other`. `ErrorBanner` supports custom action labels via `actionLabel`/`onAction` params. Retry uses exponential backoff (1s-16s cap). HTTP logging is conditional — `Level.BODY` in debug, `Level.NONE` in release (requires `buildConfig = true` in `build.gradle.kts`).
- **Analytics screen**: The Record tab. Leads with the Brier score (with help popup), Forecasts and Streak. Below: calibration bar chart, "Score over time" monthly Brier line chart (last 12 months with data, dashed 0.5 always-50% reference), an 11-week × 7-day activity heatmap (24dp cells, clickable, shows per-day count in a card below), a "Brier by tag" breakdown (sorted best-first), and the full-history sync row. Uses ALL forecasts per question (stored in `ForecastEntity` table), not just the latest — matching the Fatebook website. **Scoring inputs** (`AnalyticsViewModel.buildScoringInputs`) mirror the website's `Question | QuestionOption` model: resolved BINARY questions plus each YES/NO-resolved MC option (scored over that option's forecasts, independent of parent state); AMBIGUOUS and QUANTITY are excluded. **Brier score** is a faithful port of Fatebook's algorithm (`BrierScoring`, ported from `Sage-Future/fatebook` `lib/_scoring.ts`): it is the **two-sided** Brier (`(f-t)² + ((1-f)-(1-t))²` = `2·(f-t)²`, so an always-50% forecaster scores 0.5, range 0–2) and is **per-item time-weighted** (each day from `createdAt`→`resolvedAt` contributes equally via a time-weighted average forecast; the overall score is the unweighted mean across scored items). This requires `resolvedAt` (offline resolves stamp `resolvedAtEpochMs = now`); rows cached before it existed fall back to `resolveBy`. **Calibration chart** pools individual forecasts and is **folded to 50–100%**: forecasts below 50% are converted to `1−p` for the complement event. Ten 5%-wide uniform bars (empty buckets render as gaps), bar height = actual hit rate, **opacity encodes count** (alpha 0.3→1.0, saturating at n=10), tap a bar's column for a tooltip with exact `n`, dashed perfect-calibration line. On first visit `AnalyticsViewModel` auto-triggers **Sync full history** (all pages into Room, progress shown); afterwards `refresh()` stays in full-history mode so stats cover everything. The heatmap window aligns to whole Mon–Sun weeks, anchored to the current week's Sunday.
- **Question types**: BINARY fully supported incl. in-app creation. MULTIPLE_CHOICE: display, per-option forecasting, resolve-to-option/OTHER/AMBIGUOUS (exclusive) or per-option YES/NO (non-exclusive) — creation only on the website (API limitation). QUANTITY: read-only.
- **Home-screen widget** (Glance): ready-to-resolve count (opens the filtered feed) + quick-create button; reads Room via a Hilt EntryPoint, refreshed on app open / after sync / daily tick.
- **Offline-first reads + writes**: Room cache shows questions immediately on launch; every mutation (create, forecast, resolve, edit, delete, visibility, comment) applies to Room synchronously and is queued in `pending_mutations` for background sync. `OfflineBanner` ("You're offline. Changes will sync when you reconnect.") appears above the feed when `NetworkMonitor.isOnline` reports false. `SyncIssuesBanner` shows the count of mutations that exhausted retries; tapping "View" opens `SyncErrorsSheet` with per-row Retry/Discard.
- **Theming**: Fixed Ledger palette in light and dark, following the system setting. `themes.xml` window background matches the paper colour to avoid a launch flash.
- **Navigation gate**: `startDestination = if (hasApiKey) FEED else ONBOARDING` (sync read, no flash; users upgrading with a key never see onboarding). `hasApiKeyFlow` is observed so a key cleared at runtime drops back to onboarding. Settings remains the place to change an existing key (its back arrow is hidden when keyless).
- **ProGuard/R8**: Release builds use minification, resource shrinking, and targeted keep rules. Release buildType uses debug signing config as fallback for CI builds.
- **Rows**: overdue and due-today date lines use the accent colour (overdue adds a clock icon); resolved rows show an `OutcomeStamp` and the actual `resolvedAt` day (falling back to the deadline).
- **Accessibility**: ProbabilitySlider has semantics for screen readers. `ForecastSparkline` carries a text description. Calibration and activity charts provide text summaries via `Modifier.semantics`. Feed empty state on Active filter shows "No predictions yet / Tap + to create your first prediction".
