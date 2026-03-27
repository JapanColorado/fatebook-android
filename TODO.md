# TODO

## General

- [x] Resolved should be sorted new→old
- [x] How to do automated testing with Claude?
- [ ] Last prediction date should be right justified, in line with resolve date

## Known Bugs

- [ ] Multi-option predictions aren't working (filter out or major redesign?)
- [ ] Notifications aren't working
- [x] Prediction cards aren't clickable (only ready-to-resolve ones open the resolve sheet)
- [x] Can't make new forecasts on existing questions
- [x] Resolve sheet doesn't work
- [x] Predictions have too many decimals (60% → 60.0930823920110—float problem?)

## Polish (v1.x)

- [ ] Error handling — network errors show retry banner instead of just snackbar
- [x] Loading skeleton states while feed is refreshing
- [x] Pagination — currently loads first 100 questions only (use `cursor` from API)
- [x] Update forecast on existing question (tap question → update probability)
- [x] Search/filter questions by text
- [x] Handle `hideForecastsUntil` — don't show forecast value until date passes
- [x] Surface errors from resolve/create to user (currently silently caught)
- [x] Notification permission request flow on Android 13+
- [ ] App icon — replace placeholder with proper design

## v0.2 — Analytics

- [ ] Calibration chart — bucket resolved predictions by forecast probability, compare predicted vs. actual rates
- [ ] Streak tracker — current streak of consecutive days with >=1 prediction
- [ ] Activity trends — predictions per week/month over time
- [ ] Brier score history — score trend over time
- [ ] Analytics screen accessible from feed (new nav destination)

## Future Ideas

- [ ] Tags/categories for organizing predictions by domain
- [ ] Quick-forecast templates (pre-filled question patterns for recurring prediction types)
- [ ] Home screen widget for quick-create
- [ ] Fatebook tournament support
- [ ] Add comments to questions
- [ ] Edit question title/resolveBy/notes
- [ ] Delete questions
- [ ] Share questions publicly / toggle visibility
- [ ] Deep link support (open fatebook.io question URLs in app)
- [ ] Github Claude Code?

## Technical Debt

- [ ] Replace Moshi Kapt with pure KSP (currently triggers deprecation warning from Hilt's Kapt)
- [x] Add unit tests for QuestionRepository and ViewModels
- [x] Add UI tests for critical flows (create, resolve)
- [ ] ProGuard/R8 optimization for release builds
- [x] CI pipeline (GitHub Actions)
