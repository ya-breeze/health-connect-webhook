## Summary
- The README's Known Limitations section already documents that scheduled sync only reads a rolling **48-hour** window, so data produced while the app can't sync (phone off the home network, app killed, backend unreachable) for longer than that is never re-read and is lost.
- This adds `SyncManager.performSyncWithCatchUp()`, used by the scheduled `SyncWorker` and `SyncForegroundService` entry points. It compares now against the last *successful* sync time; when the gap exceeds the 48-hour lookback, it replays the missed period from that point forward in bounded 24-hour slices (clamped to 30 days back — Health Connect's practical retention), through the existing explicit-range `performSync(start, end)` path.
- **On by default** for scheduled/interval sync. Manual sync (`ManualSyncCard`) and the local HTTP server (`LocalTcpServerManager`) are unaffected — they already pass explicit ranges and don't go through this entry point. A normal (small) gap is a thin pass-through to the existing incremental sync, so steady-state behaviour for anyone not affected by an outage is unchanged.

## Related
- Closes #
- Related to #

## Rate limits
A multi-day replay issues many more Health Connect reads than a normal 48-hour sync — exactly
the failure mode #52 (retry + throttle) was added for. This branch adds no second rate-limit
mechanism: each catch-up slice goes through the same `performSync` → `HealthConnectManager.readHealthData` →
`readAllRecords` path as any other sync, so it inherits `withHealthConnectRetry`'s exponential
backoff and `readAllRecords`'s inter-page `READ_PAGE_DELAY_MS` throttling automatically. A small
`INTER_SLICE_DELAY_MS` (500 ms) pause between slices is the only new throttling, and it's on top
of, not instead of, the existing mechanism.

## Prior art
Searched upstream issues and PRs for `catch-up`, `catchup`, `backfill`, `offline`, `missed data`,
`48 hour`, `48h`, `lookback`, `history`, and `READ_HEALTH_DATA_HISTORY` — full results in
[`docs/upstream-pr/prior-art.md`](https://github.com/ya-breeze/health-connect-webhook/blob/main/docs/upstream-pr/prior-art.md)
on the fork's `main` (deliberately not part of this branch's diff). No prior rejection was found.
The closest related work is **PR #6, "Historical data sync"** (merged), which added *manual*
selection of an arbitrary historical date range gated behind `READ_HEALTH_DATA_HISTORY`. That
solves a different problem — a user manually pulling an old range on request — while this PR
makes the *scheduled/interval* sync recover automatically after an outage, with no user action.
The two are complementary. **PR #52, "retry + throttle Health Connect reads"** (merged) is
infrastructure this PR depends on, described above.

## Type of change
- [ ] Bug fix
- [x] New feature
- [ ] Refactor
- [ ] Documentation update
- [ ] Build/CI change

## Checklist
- [ ] I tested this change locally — **not run**: this environment has no Android SDK, so
      `./gradlew assembleDebug` / `test` / `lint` could not be executed here. See "Gates run" below.
- [x] I updated documentation (if needed) — README.md (Known Limitations, webhook format
      paragraph), docs/webhook.md, docs/local-http.md.
- [x] I added/updated tests (if needed) — `SyncManagerCatchUpTest` (14 cases) covering slice
      planning at its edges.
- [x] I verified there are no breaking changes — `performSync`'s new `updateLastSyncTime`
      parameter defaults to `true`, so no existing call site has to change. The one behaviour
      change worth calling out in review is the new `performSyncWithCatchUp` entry point, which
      `SyncWorker` and `SyncForegroundService` now call instead of `performSync`. Every other
      code path — manual sync, the local HTTP server, and `performSync` itself when the gap is
      within the lookback window — behaves exactly as before.
- [x] I checked for sensitive data/secrets

## Gates run
- `./gradlew assembleDebug`, `./gradlew test`, `./gradlew lint` — **not run**; no Android SDK
  available in the environment that prepared this branch. Verified instead by static review:
  the diff was checked file-by-file against current `upstream/main` to confirm resolved
  conflicts route through the existing `dataTypeResolutions`, `READ_PAGE_DELAY_MS`, and
  `withHealthConnectRetry` paths (no second mechanism added), and the new test file was checked
  for JUnit 4 imports, package, and naming style against `DashboardFormatterTest`.
- Please run the three Gradle commands locally (or let CI run them once this PR targets `main`)
  before merging.

## Screenshots / Recordings (if UI changes)
- N/A — no UI change.

## Additional notes
- Built directly on `upstream/main` (this fork was 26 commits behind at the time of writing);
  this diff is what would actually merge.
- This is a rebase of three commits from the fork's `feat/offline-catchup-sync` branch, reshaped
  into two upstream-style commits (`feat:` for the feature, `test:` for the unit tests) and
  rebased past upstream's own `SyncForegroundService` duplicate-run guard (added in a more recent
  upstream commit than the fork branched from) — the fork's now-redundant duplicate copy of that
  guard was dropped rather than merged in. Either commit is a valid tree on its own, so rebase,
  squash, and merge commits all work.

Created by Claude
