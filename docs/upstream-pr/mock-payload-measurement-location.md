## Summary
- `MockPayloadBuilder.kt:157` emits `put("measurement_location", "wrist")` — a **string** — for
  the mock `skin_temperature` payload.
- The real payload emits an **integer**: `SyncManager.kt:723` does
  `put("measurement_location", skin.measurementLocation)`, where
  `SkinTemperatureData.measurementLocation` is declared `Int` (`HealthConnectManager.kt:285`).
- `docs/webhook.md:154` documents the field as `number (integer)` — the AndroidX
  `SkinTemperatureRecord` location constant (unknown / finger / toe / wrist).
- The mock therefore contradicts both the real code path and the published schema. Any client
  that type-checks the mock payload against the documented/real schema breaks on this field.
- Fix: emit `3` (`SkinTemperatureRecord.MEASUREMENT_LOCATION_WRIST`) instead of the string
  `"wrist"`, matching the constant's actual value.

## Related
- Closes #
- Related to #

## Prior art
Searched for `measurement_location` and `mock payload` across upstream issues and PRs; no
existing report of this mismatch was found. The closest hit, **#25 "Add support for
SkinTemperatureRecord"** (the original feature request), confirms the field is meant to carry
Health Connect's integer `measurementLocation` — consistent with this being a genuine bug in the
mock rather than an intentional format difference. Full search table:
[`docs/upstream-pr/prior-art.md`](https://github.com/ya-breeze/health-connect-webhook/blob/feat/offline-catchup-sync-upstream/docs/upstream-pr/prior-art.md)
on the fork (not part of this branch's diff).

## Type of change
- [x] Bug fix
- [ ] New feature
- [ ] Refactor
- [ ] Documentation update
- [ ] Build/CI change

## Checklist
- [ ] I tested this change locally — **not run**: this environment has no Android SDK, so
      `./gradlew assembleDebug` / `test` / `lint` could not be executed here.
- [x] I updated documentation (if needed) — none needed; `docs/webhook.md` already documents the
      correct (integer) type, which this change now matches.
- [ ] I added/updated tests (if needed) — not added; `MockPayloadBuilder` has no existing test
      coverage to extend, and this is a one-line literal fix.
- [x] I verified there are no breaking changes — the mock endpoint is a developer/demo aid, not
      part of the real sync path; only its own output format changes, to match the type it
      always should have had.
- [x] I checked for sensitive data/secrets

## Gates run
- `./gradlew assembleDebug`, `./gradlew test`, `./gradlew lint` — **not run**; no Android SDK
  available in the environment that prepared this branch. Verified instead by reading the three
  file locations cited above directly off `upstream/main` to confirm the mismatch and the fix.

## Screenshots / Recordings (if UI changes)
- N/A

## Additional notes
- One-line change, isolated from the catch-up sync PR intentionally — it's an unrelated,
  self-contained bug fix and bundling it into a feature PR would hide it from review.

Created by Claude
