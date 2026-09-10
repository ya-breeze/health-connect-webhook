## Summary
- Add `SyncManager.performSyncWithCatchUp()` for interval and scheduled runs. When automatic delivery has been unsuccessful for more than the normal 48-hour lookback, it replays the missed range oldest first in contiguous 24-hour slices, clamped to 30 days.
- Store automatic replay progress separately from the general last-sync timestamp. Manual sync and `POST /sync` still update the user-facing timestamp and per-type cursors, but cannot hide an older automatic-delivery gap.
- Route every replay slice through the existing explicit-range `performSync` path, preserving current per-webhook filtering, JSON and Protobuf/gRPC payloads, retries, pagination throttling, notifications, and logs.
- Add JUnit 4 coverage for cursor selection/migration, normal initialization, slice checkpoint/failure behavior, the 48-hour threshold, contiguous/final boundaries, future timestamps, and the 30-day clamp.
- Add the nine missing locale sets for upstream's existing gRPC delivery strings so the required project lint gate remains green on the current base.

## Related
- Closes #
- Related to #45, #52

## Type of change
- [x] Bug fix
- [x] New feature
- [ ] Refactor
- [x] Documentation update
- [ ] Build/CI change

## Checklist
- [x] I tested this change locally
- [x] I updated documentation (if needed)
- [x] I added/updated tests (if needed)
- [x] I verified there are no breaking changes
- [x] I checked for sensitive data/secrets

## Screenshots / Recordings (if UI changes)
- N/A — catch-up has no new UI. The localization additions fill existing gRPC UI strings.

## Additional notes

### Progress and migration trade-off

`last_sync_time` remains the general status shown in the app and returned by local `/stats` and `/health`. The new `last_automatic_sync_time` preference is the only source used to plan automatic replay once it exists. A normal automatic run advances it only after `performSync` succeeds; catch-up advances it after each successful slice and never for a failed slice. The general display timestamp moves once after a complete replay.

For compatibility, the first automatic run after upgrade seeds the new preference from `last_sync_time` before doing more work. That avoids treating every existing install as having no history. The one-time trade-off is that if the legacy timestamp most recently came from a manual/API sync, the first post-upgrade seed cannot reconstruct an older automatic watermark; all later manual/API activity is isolated correctly.

### Rate limits and delivery paths

Each slice uses `performSync(start, end)`, so it inherits `HealthConnectManager`'s exponential rate-limit retry and inter-page throttle. A 500 ms inter-slice pause is added without replacing those protections. `performSync` retains upstream's `WebhookDeliveryFormat.JSON` and `WebhookDeliveryFormat.GRPC` branches unchanged, so catch-up uses the same filtering, payload builders, delivery clients, notifications, and logs.

### Prior art

The fork handoff searched upstream issues and pull requests for `catch-up`, `catchup`, `backfill`, `offline`, `missed data`, `48 hour`, `48h`, `lookback`, `history`, and `READ_HEALTH_DATA_HISTORY`; full results remain in [`docs/upstream-pr/prior-art.md`](https://github.com/ya-breeze/health-connect-webhook/blob/main/docs/upstream-pr/prior-art.md). No prior rejection was found. PR #6 added user-initiated historical-range sync, which is complementary rather than equivalent. PR #52's retry/throttle work is reused directly.

### Validation

Passed on `754389566f546c8c520852354cda3db55e57b47c`:

- `./gradlew assembleDebug`
- `./gradlew test`
- `./gradlew lint`
- `./gradlew assembleDebug` also passed independently on the retained feature parent `84e0a532b7a43075bec0f9bb3eb344c5bf133a5b`.
- The native Codex Review Gate passed for correctness, conventions, and spec/test fidelity. The independent Claude peer was unavailable because its weekly quota rejected the attempt before review.

Created by Codex
