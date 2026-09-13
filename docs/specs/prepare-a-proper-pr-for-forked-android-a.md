# Repair automatic catch-up data integrity
Idea: ya-breeze/idea-forge#419

## Why

The upstream-ready catch-up candidate at `8510dddd5dca98ce6c3f83a2c6b5fd69259772b6` preserves useful work from the earlier idea, including the dedicated `last_automatic_sync_time` preference, bounded replay slices, automatic entry points, and JUnit coverage. It is not safe to ship yet. On a normal automatic run, `SyncManager.automaticSyncRequest` leaves `updateLastSyncTime` enabled, so `performSync` can persist the later general timestamp before `runAutomaticSync` persists its captured automatic boundary. A process death between those writes can make a fresh install seed the missing automatic cursor from the later timestamp and skip records.

Automatic reads also start exactly at the committed automatic cursor. Health Connect records imported late or backdated to a measurement time before that boundary can therefore remain permanently outside later reads. Finally, `performSync` aggregates per-webhook results in its production loop, but `SyncManagerCatchUpTest` exercises mixed success and failure only through the detached `webhookBatchSucceeded` Boolean helper. The test does not prove that one successful destination plus one failed destination returns failure through the real delivery path or prevents automatic progress.

These defects invalidate the passed Review Gate claims in `docs/upstream-pr/README.md` and `docs/upstream-pr/offline-catchup-sync.md`. They must be corrected before the separate Android service lifecycle issue and final upstream handoff are completed.

## How

Preserve the candidate’s valid catch-up implementation and its `7555b53b8fa6eb3ea1bad5ae83cdfc909fbe459e` upstream base while correcting the automatic data path. Automatic orchestration, rather than `performSync`, will own both cursor writes: after a successful normal run it will persist the captured automatic boundary first and the general completion timestamp second. Failed work will advance neither value, apart from the existing one-time legacy seed, which must still be stored before delivery starts. Replay will continue checkpointing each successful slice before updating the general timestamp after the complete replay.

Use a bounded 24-hour overlap, matching the existing slice size, for automatic reads with an established cursor. Clamp the overlapped start to the existing 30-day catch-up horizon while continuing to commit the non-overlapped boundary. This provides a documented guarantee for records ingested or modified within one overlap window; older backdating remains outside the automatic guarantee and can be recovered with an explicit-range sync. Delivery remains at-least-once. Complete JSON parity with the existing Protobuf identity fields so receivers can deduplicate raw records by Health Connect `metadata.id` and version or modification time; resolution-generated aggregates must be documented as upserts keyed by data type and deterministic bucket time. Client-side exactly-once storage and unbounded historical scanning are deliberately excluded because the existing webhook protocol cannot make atomic acknowledgements across multiple destinations.

Refactor the current webhook loop only enough to expose the production aggregation path to JVM tests. Preserve per-webhook filters, JSON and gRPC payload construction, retry behavior, logs, and aggregated notifications. A mixed batch with `requireAllWebhookDeliveries` must return the real failure from this coordinator, and `runAutomaticSync` must observe that failure without moving either automatic or general progress.

This split deliberately excludes `SyncForegroundService` duplicate-start coalescing and the final reviewed/published handoff. Those form a separate Android lifecycle and release-integrity review surface and must use the candidate produced here as groundwork. This part must not create, comment on, or merge anything in `mcnaveen/health-connect-webhook`. The owner alone may later run the documented command that opens the upstream pull request.

## Ground rules
This spec is implemented by an automated pass running unattended. **There is no approval step and nothing is waiting for one** — do not look for a tick, a marker, or a sign-off anywhere, and do not wait for one.

Tick the boxes in this file as the work is completed; they are the record of progress, and the pipeline reads them to decide whether the change is finished.

Out of scope, deliberately: do NOT mark the pull request ready for review and do NOT call a forge merge API. Implementation marks the pull request ready only after the task list is complete. Afterward Completion may ask the Store to perform Automatic Merge only when the planner and final implementation agent authorized the exact result. Leave the pull request in a state worth reading.

### Task 1: Make automatic cursor persistence crash-safe

- [x] Start from the local `feat/offline-catchup-sync-upstream` candidate at `8510dddd5dca98ce6c3f83a2c6b5fd69259772b6`, preserving its upstream base, valid feature behavior, compact history, and exclusion of idea-forge handoff artifacts from the upstream-ready diff.
- [x] Change `SyncManager.automaticSyncRequest` and `performSyncWithCatchUp` so normal and replay automatic calls invoke `performSync` with general timestamp updates disabled; manual and local API callers must retain their existing `performSync` behavior.
- [x] Update `SyncManager.runAutomaticSync` so successful normal work persists the captured automatic boundary before obtaining and persisting the general completion timestamp, while failures persist neither value and the legacy seed remains the first write when migration is required.
- [x] Preserve replay semantics: checkpoint every successful slice, stop without advancing the failed slice, and write the general timestamp only after all slices succeed.
- [x] Extend `SyncManagerCatchUpTest.kt` with ordered event assertions covering a fresh install, a legacy-seeded install, successful normal delivery, normal failure, completed replay, and replay failure; explicitly prove that no execution writes the general timestamp before its automatic boundary.
- [x] Mark completed

### Task 2: Recover bounded late ingestion without moving progress backward

- [x] Add a named 24-hour automatic overlap beside `GAP_THRESHOLD_HOURS`, `SLICE_HOURS`, and `MAX_CATCHUP_DAYS`, and centralize calculation of an automatic request’s effective read start.
- [x] Apply the overlap to normal automatic reads and replay slices that have a committed start, clamp it to the existing 30-day horizon, and continue checkpointing the original slice or run boundary rather than the overlapped start.
- [x] Keep first-use behavior unchanged when neither automatic nor legacy progress exists, and keep explicit manual/API ranges and per-type cursors outside the overlap policy.
- [x] Extend `putRecordMetadata` in `SyncManager.kt` to serialize the stable identity, version, modification-time, and applicable zone-offset fields already carried by `RecordMetadata`, matching the existing Protobuf representation and field names documented in `docs/webhook.md`.
- [x] Add orchestration regressions showing that a record timestamped before the committed cursor but within the overlap is inside the next automatic request, that the cursor still advances only to the captured boundary after success, and that the overlap never reads before the 30-day cap.
- [x] Update `README.md`, `docs/webhook.md`, and `docs/local-http.md` with the bounded late-ingestion guarantee, at-least-once delivery trade-off, raw-record deduplication keys, aggregate upsert key, and explicit-range recovery for older backdated data.
- [x] Mark completed

### Task 3: Test partial delivery through the production aggregation path

- [x] Extract the per-webhook delivery aggregation currently embedded in `SyncManager.performSync` into an internal production coordinator with injectable delivery calls, and have `performSync` use that coordinator rather than duplicating its decision logic.
- [x] Preserve enabled-webhook selection, data-type filtering, JSON and gRPC branches, payload-size handling, failure selection, webhook logging, and notification aggregation while representing attempted successes and failures in one production result.
- [x] Make `requireAllWebhookDeliveries = true` return failure when any attempted destination fails even if another succeeds; retain the existing any-success behavior for manual/API calls that pass `false`.
- [x] Replace reliance on the detached `webhookBatchSucceeded` test with coverage that sends one success and one failure through the coordinator used by `performSync`, feeds that result through `runAutomaticSync`, and proves neither automatic nor general progress advances.
- [x] Cover all-success, all-failure, no-matching-data, JSON/gRPC, and filtered-webhook cases sufficiently to show the refactor preserved existing delivery behavior.
- [x] Mark completed

### Task 4: Leave an honest, buildable intermediate candidate

- [x] Keep the upstream-ready diff limited to the catch-up implementation, its tests and user-facing documentation, the existing localization lint repair, and the JSON metadata parity required by overlap deduplication; do not include `docs/specs/`, `docs/upstream-pr/`, dependency upgrades, cleartext policy, or the deferred service lifecycle change.
- [x] Run the project’s existing assemble, unit-test, and lint checks against the corrected data-path candidate and fix regressions, retaining only buildable commits.
- [x] Commit the corrected data-path candidate locally for the deferred lifecycle work, but do not publish the intermediate ref or describe it as the final reviewed upstream candidate.
- [x] In `docs/upstream-pr/README.md` and `docs/upstream-pr/offline-catchup-sync.md`, remove the stale passed-gate assertion and final-candidate status, reset review-dependent checklist claims, remove the empty `Closes #` placeholder, and state that lifecycle remediation and final review remain pending without inventing a replacement final SHA.
- [x] Mark completed
