## Summary

- Add `SyncManager.performSyncWithCatchUp()` for interval and scheduled runs. When automatic delivery has been unsuccessful for more than the normal 48-hour lookback, it replays the missed range oldest first in contiguous 24-hour slices, clamped to 30 days.
- Store automatic replay progress separately from the general last-sync timestamp. Manual sync and `POST /sync` still update the user-facing timestamp and per-type cursors, but cannot hide an older automatic-delivery gap. Automatic progress advances only when every attempted webhook delivery succeeds.
- Route every replay slice through the existing explicit-range `performSync` path, preserving current per-webhook filtering, JSON and Protobuf/gRPC payloads, retries, pagination throttling, notifications, and logs.
- Add `SyncForegroundServiceLifecycleCoordinator` so duplicate starts coalesce, retained schedules are rescheduled once, stale completion cannot stop a newer generation, timeout and destruction clean up safely, and one alarm-reschedule failure cannot suppress other schedules or service shutdown.
- Add JVM coverage for cursor migration/order, bounded overlap and future cursors, automatic failure semantics, mixed webhook delivery and payload-build failures, and the lifecycle race and cleanup invariants.
- Add the nine missing locale sets for upstream's existing gRPC delivery strings so the required project lint gate remains green on the current base.

## Candidate record

- Base: `7555b53b8fa6eb3ea1bad5ae83cdfc909fbe459e`
- Final reviewed head: `4604b7a2d252cad740f19c86d71aa39dc78705c7`
- Durable local ref: `candidate/idea-599-offline-catchup-sync`
- Ordered commits:
  1. `478fd2904fb8d0d64fb2a60b160c534c4ee5bbaf` — `fix: make automatic catch-up cursor persistence crash-safe and bounded`
  2. `4604b7a2d252cad740f19c86d71aa39dc78705c7` — `fix: coordinate foreground service lifecycle`
- Publication status: pending; this document has not been submitted to the upstream fork.

## Related

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
- [x] I verified there are no breaking changes — Review Gate passed for `4604b7a2d252cad740f19c86d71aa39dc78705c7`
- [x] I checked for sensitive data/secrets

## Screenshots / Recordings (if UI changes)

- N/A — catch-up has no new UI. The localization additions fill existing gRPC UI strings; the log filter includes automatic replay entries in the existing Auto view.

## Additional notes

### Progress and migration trade-off

`last_sync_time` remains the general status shown in the app and returned by local `/stats` and `/health`. The new `last_automatic_sync_time` preference is the source used to plan every automatic read once it exists, so manual/API per-type cursors cannot suppress records from a normal sub-48-hour automatic run. Automatic orchestration, not `performSync`, owns both writes: a normal run persists the captured automatic boundary first and the general timestamp second, only after `performSync` succeeds, so a crash between the two can never leave the general timestamp ahead of the automatic cursor. Catch-up advances the automatic boundary after each successful slice and never for a failed slice, moving the general timestamp once after a complete replay. Automatic calls require every attempted webhook delivery to succeed before shared progress advances, while manual/API calls retain the existing any-success behavior. Every automatic read also starts 24 hours before its cursor (clamped to the 30-day catch-up horizon) to recover records ingested or modified shortly after the cursor passed them; receivers dedupe the resulting at-least-once redelivery using the record identity fields documented in `docs/webhook.md`.

For compatibility, the first automatic run after upgrade seeds the new preference from `last_sync_time` before doing more work. That avoids treating every existing install as having no history. The one-time trade-off is that if the legacy timestamp most recently came from a manual/API sync, the first post-upgrade seed cannot reconstruct an older automatic watermark; all later manual/API activity is isolated correctly.

### Rate limits and delivery paths

Each slice uses `performSync(start, end)`, so it inherits `HealthConnectManager`'s exponential rate-limit retry and inter-page throttle. A 500 ms inter-slice pause is added without replacing those protections. `performSync` retains upstream's `WebhookDeliveryFormat.JSON` and `WebhookDeliveryFormat.GRPC` branches unchanged, so catch-up uses the same filtering, payload builders, delivery clients, notifications, and logs. The 25,000-record payload cap still applies after the 24-hour overlap; a failed oversized read leaves the automatic cursor unchanged for a later retry.

### Lifecycle coordination

`SyncForegroundService` now delegates generation state to `SyncForegroundServiceLifecycleCoordinator`. It retains every coalesced schedule ID and newest start ID, launches at most one active sync, reschedules each retained enabled schedule after completion or timeout, suppresses stale stop requests when a newer generation is active, ignores starts after destruction, and isolates per-schedule reschedule failures. Timeout detaches state before cancelling the job so the coroutine's late `finally` block cannot double-finalize the run.

### Prior art

The fork handoff searched upstream issues and pull requests for `catch-up`, `catchup`, `backfill`, `offline`, `missed data`, `48 hour`, `48h`, `lookback`, `history`, and `READ_HEALTH_DATA_HISTORY`; full results remain in [`docs/upstream-pr/prior-art.md`](https://github.com/ya-breeze/health-connect-webhook/blob/main/docs/upstream-pr/prior-art.md). No prior rejection was found. PR #6 added user-initiated historical-range sync, which is complementary rather than equivalent. PR #52's retry/throttle work is reused directly.

## Validation

The detached clean validation worktree passed every complete gate at the exact reviewed SHA `4604b7a2d252cad740f19c86d71aa39dc78705c7`:

```text
./gradlew assembleDebug  PASS
./gradlew test           PASS
./gradlew lint           PASS
```

The unit-test result files for each `fossDebug`, `fossRelease`, `playstoreDebug`, and `playstoreRelease` variant report:

- `SyncManagerCatchUpTest`: 36 tests
- `SyncManagerWebhookDeliveryTest`: 9 tests
- `SyncForegroundServiceLifecycleTest`: 9 tests

## Review Gate

A fresh Review Gate reviewed exactly `7555b53b8fa6eb3ea1bad5ae83cdfc909fbe459e..4604b7a2d252cad740f19c86d71aa39dc78705c7` in the clean candidate worktree. It covered automatic-cursor ordering and overlap, webhook-delivery aggregation, lifecycle races and cleanup, regression coverage, upstream-base fidelity, compact history, and absence of fork-only artifacts. The final result was **no unresolved verified findings**.

The review found no production-code defect. Verified coverage gaps were fixed with regression tests for mixed success plus `PayloadBuildFailed` delivery and future automatic cursors. Earlier verified documentation/log-filter/overlap findings were also folded into the integration commit and re-reviewed at this same final SHA. The documented payload-cap behavior, base-preserved destroy behavior, and minor scope/style observations were investigated and demonstrated not to be defects.

## Upstream-only handoff boundary

The reviewed range contains no `docs/specs/`, `docs/upstream-pr/`, manifest, network-security, Gradle/dependency/version, `MockPayloadBuilder.kt`, dependency-upgrade, cleartext-policy, or checkpoint artifacts. These handoff files exist only on the idea branch. Preparing them did not alter the local candidate ref, candidate SHA, history, upstream diff, or any fork artifact.

## Owner-only publication command

Publication remains pending and this command has not been run. The owner may run it only after deciding to publish the reviewed candidate:

```bash
gh pr create --repo mcnaveen/health-connect-webhook --head ya-breeze:feat/offline-catchup-sync-upstream --base main --body-file docs/upstream-pr/offline-catchup-sync.md
```

Do not push the candidate branch or edit the existing fork pull-request description in this phase. No command here may create, comment on, review, merge, or otherwise mutate any artifact in `mcnaveen/health-connect-webhook`.

Created by Codex
