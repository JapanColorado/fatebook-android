# TODO

- [x] Make Articon icon (https://docs.arcticons.com/contribute)
- [x] Enable GitHub Pages on repo (Settings > Pages > deploy from `/docs` on `main`) for privacy policy
- [ ] Set up Play Store signing (replace debug signing fallback in release buildType)

## Feature Ideas

- [ ] Quick-forecast templates (pre-filled question patterns for recurring prediction types)
- [ ] Home screen widget for quick-create
- [ ] Deep linking for fatebook.io/q/* URLs (requires Android App Links domain verification or custom scheme)
- [x] Ready to resolve notifications
- [x] Fix make a daily prediction notification

---

## Archive

### Brier score accuracy (match fatebook.io)
- [x] Brier score was off — diagnosed against Fatebook's source (`Sage-Future/fatebook` `lib/_scoring.ts`): app used the one-sided Brier (half of Fatebook's two-sided value) and pooled all forecasts into one flat average
- [x] New `BrierScoring` faithfully ports Fatebook's algorithm: two-sided Brier (`2·(f-t)²`, range 0–2, always-50% → 0.5) and per-question daily time-weighting, then equal-weight mean across questions (a heavily-updated question no longer dominates)
- [x] Plumbed `resolvedAt` through `QuestionDto` → `QuestionEntity` (DB 9→10) → `Question`; offline resolves stamp `resolvedAtEpochMs = now`; pre-existing cache rows fall back to `resolveBy`
- [x] Tests: `BrierScoringTest` (two-sided formula, time-weighting, fractional days, per-question weighting, edge cases) + rewritten `AnalyticsViewModelTest` Brier section + repository `resolvedAt` mapping/offline-resolve assertions

### Offline writes
- [x] Optimistic write architecture: every mutation applies to Room synchronously and is queued in `pending_mutations` for background sync by a `SyncWorker` (NetworkType.CONNECTED constraint)
- [x] Offline-created questions use `local-<uuid>` ids; reconcile to the server id by parsing the `--<cuid>` suffix of the URL returned by `createQuestion` (with title+createdAt window as fallback)
- [x] Reconciliation deletes the local-id row instead of renaming the PK (avoids `UNIQUE constraint failed` when the server row is already in cache from `refresh()`)
- [x] Retry-safe: before re-calling `api.createQuestion` on a previously-errored CREATE, check for an existing server copy and skip the API call
- [x] `[CREATE_local, DELETE_local]` collapses to a noop at enqueue time
- [x] `NetworkMonitor` via `registerDefaultNetworkCallback` exposes `isOnline: StateFlow<Boolean>` (dropped `NET_CAPABILITY_VALIDATED` requirement — that flag lags real connectivity and was hiding genuine offline transitions)
- [x] `OfflineBanner` shows above the feed when offline; `SyncIssuesBanner` + `SyncErrorsSheet` surface mutations that exhausted retries with per-row Discard / Retry-all (Discard of an errored CREATE auto-removes the local duplicate if a server copy exists)
- [x] Tests: `SyncRunner` happy path, IOException → RETRY, 5 HTTP failures → ERRORED, URL parsing (canonical + slug-with-double-dash + missing suffix), retry-skip, and Paparazzi snapshots for both banners

### v0.2.2 -- Detail sheet fixes
- [x] Detail sheet showed pre-edit values after editing (stale closure: `QuestionCard` cached its click lambda with `remember(question.id)`, so the captured `Question` reference didn't refresh when other fields changed; switched to `rememberUpdatedState`)
- [x] `resolvesLabel` said "Resolved" for unresolved-but-overdue questions; now only based on the `resolved` flag (the feed card already used that flag — the detail sheet was the outlier)

### v0.2.1 -- Performance
- [x] Set-diff refresh inside a single Room transaction (no more empty→full flash on every refresh)
- [x] Replace post-mutation `refresh()` calls with targeted DAO updates (addForecast, resolveQuestion, editQuestion, setSharedPublicly)
- [x] AnalyticsViewModel observes the cache instead of eagerly paginating all pages on init
- [x] Split FeedViewModel Flow chain so filter/search don't restart the whole pipeline
- [x] Mark Question/Forecast/Comment as `@Immutable` for Compose stability
- [x] Add `contentType` to LazyColumn items so compositions are reused during scroll
- [x] Hoist DateTimeFormatters in QuestionCard to file scope
- [x] Use `Card(onClick = ...)` with a remembered click handler instead of inline lambda + `Modifier.clickable`
- [x] Index `resolved` + `resolveByEpochMs` columns on questions (DB version 7→8)

### v0.3 -- Question Management & Deep Links
- [x] Fix: "Resolves today" shown as 22 hr overdue (compare dates at day level, not instant level)
- [x] Add comments to questions (via API addComment endpoint)
- [x] Edit question title/resolveBy/notes (via API editQuestion endpoint)
- [x] Delete questions (via API deleteQuestion endpoint, with confirmation dialog)
- [x] Share questions publicly / toggle visibility (Android share intent + API setSharedPublicly)
- [x] ~Deep link support~ (removed — Android 12+ defaults https links to browser without domain verification)

### General
- [x] Date selector shouldn't be dim-grayed out in prediction card if you selected a different date than that day.
- [x] Resolution dates are one day off. (mar 27 → resolve any time on mar. 27, not after march 27)
- [x] Dots on graph should be selectable and display the number for that data point.
- [x] Graph appears to not use all data points (39 in app vs ~65 in website)
- [x] "Lower is better" text should be pop up when Brier Score is click on, and a little question mark help icon should indicate it's clickable
- [x] "Brier Score" text should be size of the number and in line with number, eg. 0.12 Brier Score
- [x] Bars should be clickable on bar graph, with it showing the number of predictions on the day and the specific date when clicked
- [x] The prediction number and date should be center aligned (especially on long-text cards)
- [x] Resolved should be sorted new->old
- [x] How to do automated testing with Claude?
- [x] Last prediction date should be right justified, in line with resolve date
- [x] Search bar should autocapitalize
- [x] I select a date in the resolve by, and then it shows up as the day before in the resolve by bar and then is all weird as well in the feed. Also, when I say resolve today, it doesn't show as ready to resolve.
- [x] "Predicted by x" text should be all the way right justified (below percentage)
- [x] 400 Error when deleting question
- [x] Public/Private text should be under eye icon in card
- [x] Open in Fatebook button should be the Fatebook logo in a circle with the edit, delete, share, and publicity icons.
- [x] Resolve card should be the same as the forecast card, just with the resolve buttons replacing the update forecast slider and buttons.
- [x] "required value 'id' missing at $" error when adding a comment.
- [x] "Resolves" in forecast card should be "Resolved" when the date is in the past.

### Known Bugs
- [x] Multi-option predictions aren't working (filter out or major redesign?)
- [x] Notifications aren't working
- [x] Prediction cards aren't clickable (only ready-to-resolve ones open the resolve sheet)
- [x] Can't make new forecasts on existing questions
- [x] Resolve sheet doesn't work
- [x] Predictions have too many decimals (60% -> 60.0930823920110--float problem?)
- [x] All dates have off by one errors—simplify date set up so that resolution happens on a certain day, defaulting to tomorrow. Use local time zone.

### Polish (v1.x)
- [x] Error handling -- network errors show retry banner instead of just snackbar
- [x] Loading skeleton states while feed is refreshing
- [x] Pagination -- currently loads first 100 questions only (use `cursor` from API)
- [x] Update forecast on existing question (tap question -> update probability)
- [x] Search/filter questions by text
- [x] Handle `hideForecastsUntil` -- don't show forecast value until date passes
- [x] Surface errors from resolve/create to user (currently silently caught)
- [x] Notification permission request flow on Android 13+
- [x] App icon -- replace placeholder with proper design

### v0.2 -- Analytics
- [x] Calibration chart -- bucket resolved predictions by forecast probability, compare predicted vs. actual rates
- [x] Streak tracker -- current streak of consecutive days with >=1 prediction
- [x] Activity trends -- predictions per week/month over time
- [x] Brier score history -- score trend over time
- [x] Analytics screen accessible from feed (new nav destination)

### Technical Debt
- [x] Replace Moshi Kapt with pure KSP (already using KSP -- no kapt in the project)
- [x] Add unit tests for QuestionRepository and ViewModels
- [x] Add UI tests for critical flows (create, resolve)
- [x] ProGuard/R8 optimization for release builds
- [x] CI pipeline (GitHub Actions)
