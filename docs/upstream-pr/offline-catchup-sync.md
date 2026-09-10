## Summary
- Add `SyncManager.performSyncWithCatchUp()` for interval and scheduled runs. When automatic delivery has been unsuccessful for more than the normal 48-hour lookback, it replays the missed range oldest first in contiguous 24-hour slices, clamped to 30 days.
- Store automatic replay progress separately from the general last-sync timestamp. Manual sync and `POST /sync` still update the user-facing timestamp and per-type cursors, but cannot hide an older automatic-delivery gap. Automatic progress advances only when every attempted webhook delivery succeeds.
- Route every replay slice through the existing explicit-range `performSync` path, preserving current per-webhook filtering, JSON and Protobuf/gRPC payloads, retries, pagination throttling, notifications, and logs.
- Add JUnit 4 coverage for cursor selection/migration, the actual automatic orchestration's normal initialization and slice checkpoint/failure behavior, all-destination delivery success, the 48-hour threshold, contiguous/final boundaries, future timestamps, and the 30-day clamp.
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

`last_sync_time` remains the general status shown in the app and returned by local `/stats` and `/health`. The new `last_automatic_sync_time` preference is the source used to plan every automatic read once it exists, so manual/API per-type cursors cannot suppress records from a normal sub-48-hour automatic run. A normal automatic run advances it to the boundary captured before its read, and only after `performSync` succeeds; this leaves records created while that sync is finishing on the replayable side of the cursor. Catch-up advances it after each successful slice and never for a failed slice. Automatic calls require every attempted webhook delivery to succeed before shared progress advances, while manual/API calls retain the existing any-success behavior. The general display timestamp moves once after a complete replay.

For compatibility, the first automatic run after upgrade seeds the new preference from `last_sync_time` before doing more work. That avoids treating every existing install as having no history. The one-time trade-off is that if the legacy timestamp most recently came from a manual/API sync, the first post-upgrade seed cannot reconstruct an older automatic watermark; all later manual/API activity is isolated correctly.

### Rate limits and delivery paths

Each slice uses `performSync(start, end)`, so it inherits `HealthConnectManager`'s exponential rate-limit retry and inter-page throttle. A 500 ms inter-slice pause is added without replacing those protections. `performSync` retains upstream's `WebhookDeliveryFormat.JSON` and `WebhookDeliveryFormat.GRPC` branches unchanged, so catch-up uses the same filtering, payload builders, delivery clients, notifications, and logs.

### Prior art

The fork handoff searched upstream issues and pull requests for `catch-up`, `catchup`, `backfill`, `offline`, `missed data`, `48 hour`, `48h`, `lookback`, `history`, and `READ_HEALTH_DATA_HISTORY`; full results remain in [`docs/upstream-pr/prior-art.md`](https://github.com/ya-breeze/health-connect-webhook/blob/main/docs/upstream-pr/prior-art.md). No prior rejection was found. PR #6 added user-initiated historical-range sync, which is complementary rather than equivalent. PR #52's retry/throttle work is reused directly.

### Validation

Passed on `def32a2216b05cca5687408e9c76a02fb1ccf5bf`:

- `./gradlew assembleDebug`
- `./gradlew test`
- `./gradlew lint`
- `./gradlew assembleDebug` also passed independently on the retained feature parent `94de8927778a1159a4653fe2ce1e18c825f68a24`.
- The native Codex Review Gate passed for correctness, conventions, and spec/test fidelity. The independent Claude peer was unavailable because its weekly quota rejected the attempt before review.

Created by Codex
