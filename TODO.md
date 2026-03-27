# TODO

## General

- [ ] Date selector shouldn't be dim-grayed out in prediction card if you selected a different date than that day.
- [ ] Resolution dates are one day off. (mar 27 → resolve any time on mar. 27, not after march 27)
- [ ] Dots on graph should be selectable and display the number for that data point.
- [ ] Graph appears to not use all data points (39 in app vs ~65 in website)
- [ ] "Lower is better" text should be pop up when Brier Score is click on, and a little question mark help icon should indicate it's clickable
- [ ] "Brier Score" text should be size of the number and in line with number, eg. 0.12 Brier Score
- [ ] Bars should be clickable on bar graph, with it showing the number of predictions on the day and the specific date when clicked

## Known Bugs

(none)

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

---

## Archive

### General
- [x] Resolved should be sorted new->old
- [x] How to do automated testing with Claude?
- [x] Last prediction date should be right justified, in line with resolve date
- [x] Search bar should autocapitalize
- [x] I select a date in the resolve by, and then it shows up as the day before in the resolve by bar and then is all weird as well in the feed. Also, when I say resolve today, it doesn't show as ready to resolve.
- [x] "Predicted by x" text should be all the way right justified (below percentage)

### Known Bugs
- [x] Multi-option predictions aren't working (filter out or major redesign?)
- [x] Notifications aren't working
- [x] Prediction cards aren't clickable (only ready-to-resolve ones open the resolve sheet)
- [x] Can't make new forecasts on existing questions
- [x] Resolve sheet doesn't work
- [x] Predictions have too many decimals (60% -> 60.0930823920110--float problem?)

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
