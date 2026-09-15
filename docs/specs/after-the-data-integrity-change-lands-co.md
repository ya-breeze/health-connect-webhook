# Fix duplicate foreground-sync service lifecycle handling
Idea: ya-breeze/idea-forge#482

## Why

The completed data-integrity groundwork in `docs/specs/prepare-a-proper-pr-for-forked-android-a.md` deliberately deferred an independent lifecycle defect in `app/src/main/java/com/hcwebhook/app/SyncForegroundService.kt`. The corrected catch-up implementation is available at `feat/offline-catchup-sync-corrected` commit `478fd2904fb8d0d64fb2a60b160c534c4ee5bbaf` and is already represented in this idea branch; the stale published candidate `8510dddd5dca98ce6c3f83a2c6b5fd69259772b6` must not be reused as the implementation baseline.

`SyncForegroundService.onStartCommand` currently guards work with the companion-wide `isSyncRunning` `AtomicBoolean`. When a second start arrives while the first catch-up coroutine is active, the duplicate path calls `stopSelf(startId)` using the newest start ID. Android may therefore destroy the service immediately, after which `onDestroy` cancels the service `SupervisorJob` and the original sync coroutine. Removing only that duplicate `stopSelf` is also insufficient: the surviving coroutine later calls `stopSelf` with its older start ID, which no longer identifies the newest request and can leave the foreground service running indefinitely. Duplicate scheduled starts also carry schedule IDs whose next alarms must not be lost while the requests are coalesced.

This defect matters now because `performSyncWithCatchUp` can legitimately run through multiple replay slices. A duplicate alarm during that longer operation must share the surviving run rather than terminate it, and the service must still stop promptly and exactly once when that run completes or Android invokes `onTimeout`.

## How

Replace the static Boolean guard with instance-owned, synchronized lifecycle coordination used directly by `SyncForegroundService`. The coordinator will own the active coroutine, the newest observed start ID, and a deduplicated ordered collection of non-null schedule IDs. Every `onStartCommand` records its start metadata before deciding whether work is already active. A duplicate returns `START_NOT_STICKY` without launching another sync, calling `stopSelf`, rescheduling prematurely, or cancelling the active job.

Route normal completion, exceptions, `OutOfMemoryError`, and timeout through one idempotent finishing path. That path will reschedule every retained schedule ID through the existing `rescheduleAlarmIfNeeded` behavior, which already ignores deleted or disabled schedules, and stop against the newest start ID only after the surviving operation has finished or timed out. Use generation-aware state and `stopSelfResult` or equivalent newest-start protection so a stale completion cannot stop a genuinely newer run that arrived during teardown. `onTimeout` must cancel the active sync before completing the service, while `onDestroy` remains responsible for cancellation when Android destroys the service for an external reason.

Keep the Android-facing service thin and expose only an internal, production-used coordination seam for deterministic JVM tests. This avoids adding Robolectric or changing `app/build.gradle.kts` and `gradle/libs.versions.toml`, while still testing the exact state machine that decides whether the service launches, cancels, reschedules, or requests a stop. Preserve foreground notification setup, `START_NOT_STICKY`, `SyncManager.performSyncWithCatchUp(syncType = "auto")`, exception logging, and the `ScheduledSyncReceiver`/`ScheduledSyncManager` contract. Do not alter the corrected cursor, overlap, delivery aggregation, payload, or user-facing documentation behavior in this part.

Final upstream-candidate assembly, the pinned-SHA Review Gate, publication of `origin/feat/offline-catchup-sync-upstream`, and handoff-document refresh are deferred to the follow-up because they form a distinct release-integrity change that depends on this lifecycle fix landing. Nothing in this part may create, comment on, or merge an artifact in `mcnaveen/health-connect-webhook`. The eventual owner-only command remains `gh pr create --repo mcnaveen/health-connect-webhook --head ya-breeze:feat/offline-catchup-sync-upstream --base main --body-file docs/upstream-pr/offline-catchup-sync.md`; document it in the final handoff, but do not execute it.

## Ground rules
This spec is implemented by an automated pass running unattended. **There is no approval step and nothing is waiting for one** — do not look for a tick, a marker, or a sign-off anywhere, and do not wait for one.

Tick the boxes in this file as the work is completed; they are the record of progress, and the pipeline reads them to decide whether the change is finished.

Out of scope, deliberately: do NOT mark the pull request ready for review and do NOT call a forge merge API. Implementation marks the pull request ready only after the task list is complete. Afterward Completion may ask the Store to perform Automatic Merge only when the planner and final implementation agent authorized the exact result. Leave the pull request in a state worth reading.

### Task 1: Preserve the corrected catch-up baseline

- [x] Base the lifecycle work on the data-integrity implementation represented by `feat/offline-catchup-sync-corrected` at `478fd2904fb8d0d64fb2a60b160c534c4ee5bbaf`, as incorporated into this branch, rather than the stale `8510dddd5dca98ce6c3f83a2c6b5fd69259772b6` candidate.
- [x] Keep the corrected `SyncManager.performSyncWithCatchUp`, automatic cursor ordering, overlap, JSON metadata, and webhook delivery coordinator behavior unchanged while isolating the service fix.
- [x] Record the lifecycle invariants in code comments near the new coordination state: one active run per service instance, every start contributes its newest start ID and optional schedule ID, and only completion or timeout may request a stop.
- [x] Mark completed

### Task 2: Add race-safe lifecycle coordination

- [x] Introduce an internal production-used lifecycle coordinator beside `SyncForegroundService` that owns the active `Job`, newest start ID, retained schedule IDs, and an idempotent completion generation under one synchronization strategy.
- [x] Make its start operation retain each non-null schedule ID and update the newest start ID before returning either a launch decision for an idle service or a coalesced decision for an active service.
- [x] Ensure a coalesced start performs no stop, cancellation, immediate reschedule, or second sync launch.
- [x] Make normal completion atomically detach the completed generation, drain its retained schedule IDs, and identify the newest start ID that may safely be stopped; a start arriving after that transition must launch a new generation and be protected from the stale completion.
- [x] Make timeout cancellation and completion idempotent so the cancelled coroutine's `finally` block cannot reschedule or stop the service a second time.
- [x] Mark completed

### Task 3: Wire `SyncForegroundService` through the coordinator

- [x] Replace companion `isSyncRunning` and its `AtomicBoolean` import with instance-scoped coordination in `SyncForegroundService.kt`.
- [x] Update `onStartCommand` to delegate every start, launch `syncManager.performSyncWithCatchUp(syncType = "auto")` only for the surviving generation, and return `START_NOT_STICKY` without calling `stopSelf(startId)` for duplicates.
- [x] Route successful results, returned failures, thrown exceptions, and `OutOfMemoryError` through the same finalization path, rescheduling each retained ID once via `rescheduleAlarmIfNeeded` before stopping against the newest safe start ID.
- [x] Update `onTimeout` to cancel the surviving run and finalize against the coordinator's newest start state rather than blindly clearing a global flag and stopping the callback's possibly stale ID.
- [x] Keep `onDestroy` safe for both normal self-stop and external destruction: it must release coordinator resources and cancel genuinely active work without allowing an old completion to affect a later generation.
- [x] Refresh the service KDoc and logs so they describe duplicate coalescing, retained alarm rescheduling, and newest-start shutdown accurately.
- [x] Mark completed

### Task 4: Add lifecycle regression coverage

- [x] Add `app/src/test/java/com/hcwebhook/app/SyncForegroundServiceLifecycleTest.kt` covering the internal coordinator used by the production service, using a controllably suspended coroutine and recorded launch, cancellation, reschedule, and stop effects.
- [x] Prove that start ID 1 launches one run and a duplicate start ID 2 launches no second run, emits no stop request, emits no cancellation, leaves the original coroutine active, and retains both distinct schedule IDs.
- [x] Release the surviving run and prove retained schedule IDs are each rescheduled once and the sole stop request uses newest start ID 2 rather than the original ID.
- [x] Cover repeated identical schedule IDs and null schedule IDs so coalescing neither duplicates alarms nor invents scheduled work.
- [x] Cover timeout after duplicate starts: the active coroutine is cancelled once, retained schedules are handled once, the newest start is stopped once, and the cancelled coroutine's eventual cleanup produces no duplicate effects.
- [x] Cover a start racing with or following completion so a stale generation cannot cancel or stop the newly launched run, plus external destruction so an actually active coroutine is cancelled.
- [x] Mark completed

### Task 5: Validate the isolated lifecycle change

- [ ] Run `./gradlew test` and confirm the new lifecycle regressions execute for both application flavors alongside `SyncManagerCatchUpTest`.
- [ ] Run `./gradlew assembleDebug` and `./gradlew lint`, fixing any regression introduced by the service refactor without weakening existing lint or tests.
- [ ] Inspect the resulting change so it remains limited to `SyncForegroundService.kt`, its lifecycle tests, and only directly necessary supporting code; do not update or publish the final upstream candidate or its handoff records in this part.
- [ ] Mark completed
