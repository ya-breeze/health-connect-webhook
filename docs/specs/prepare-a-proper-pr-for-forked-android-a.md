# Correct and republish the upstream catch-up sync branch
Idea: ya-breeze/idea-forge#183

## Why

The existing upstream handoff is not ready to ship. `docs/upstream-pr/README.md` identifies `777ff1d6ede736a982506e220635f96d78767cc5` as the catch-up branch base and `e06ee928bc1b52340f252bf81d670607a1d0daae` as its head, but the local `feat/offline-catchup-sync-upstream` branch still points at the older four-commit history ending at `0032356`, while the fork-tracking ref points at the documented two-commit history. Review and handoff must refer to one identical, remotely verifiable commit. In addition, upstream `main` has advanced to `6a65d67` through the Protobuf/gRPC work, six commits beyond the documented base, so the candidate must be rebuilt on the current fetched upstream tip and preserve the new JSON and gRPC delivery paths in `SyncManager.performSync`.

The catch-up implementation also has a data-loss defect. `SyncManager.performSyncWithCatchUp` currently plans replay from `PreferencesManager.getLastSyncTime()` and checkpoints progress through `setLastSyncTime()`. That same preference is updated by ordinary `performSync` calls, including the manual paths in `components/ManualSyncCard.kt` and the local API path in `LocalTcpServerManager.handleSyncRequest`. A successful one-day manual or API sync can therefore replace an older automatic-delivery watermark; the next `SyncWorker` or `SyncForegroundService` run sees only a short gap and never replays the missed scheduled-delivery period. This must be corrected before an upstream pull request is offered because it defeats the feature's central guarantee.

The existing investigation and most of the feature remain valid: catch-up should still use bounded slices, upstream's retry and pagination throttling, the single `SyncManager` companion object, and the existing automatic entry points. The unrelated `NoMatchingData` cursor update must remain excluded. Current upstream has independently removed the need for the proposed mock-payload pull request: `MockPayloadBuilder.kt` now emits integer `measurementLocation` values, matching `SyncManager.kt` and `docs/webhook.md`. The catch-up correction is therefore the first coherent submission to finish.

## How

Rebuild `feat/offline-catchup-sync-upstream` directly on the current fetched `upstream/main`, preserving the reviewed catch-up behavior and documentation while resolving against upstream's newer Protobuf/gRPC implementation. Keep a compact upstream-facing history in which every commit is buildable. The branch must contain only the catch-up feature and its tests; dependency upgrades, cleartext-network policy, mock-payload changes, idea-forge specs, and `docs/upstream-pr/` artifacts remain outside its diff.

Separate automatic catch-up progress from the general last-sync timestamp. Add a dedicated `SharedPreferences` key and paired getter/setter in `PreferencesManager.kt`, following its existing `KEY_…`, nullable-long getter, and setter conventions. `KEY_LAST_SYNC_TIME` remains the user-facing timestamp consumed by `ConfigurationScreen` and the local `/stats` and `/health` responses. `performSyncWithCatchUp` must plan exclusively from the dedicated automatic-sync cursor once it exists. For compatibility, a missing new cursor may be seeded once from the legacy general timestamp; persist that seed before later manual or API activity can affect planning. A normal automatic run advances the dedicated cursor only after `performSync` succeeds. During a replay, advance it to each successfully completed slice boundary so interruption resumes from that boundary, never advance it for a failed slice, and update the general display timestamp only after the overall automatic operation succeeds. Manual and API calls continue updating the general timestamp and per-type cursors through `performSync`, but must never write the automatic catch-up cursor.

Retain the existing 48-hour threshold, 24-hour slices, 30-day cap, explicit-range reads, inter-slice delay, and upstream retry/throttle paths unless review demonstrates a correctness problem. Update `README.md`, `docs/webhook.md`, and `docs/local-http.md` so they describe the dedicated last successful automatic-sync progress rather than the ambiguous last successful sync. Preserve the prior-art conclusion for catch-up, but remove stale claims that the branch could not be compiled or tested.

Run the real Gradle checks and the complete Codex Review Gate against a pinned candidate SHA before publishing it. The native review must cover correctness, repository conventions, and specification/test fidelity; request the independent Claude review once when available, record `peer unavailable` with its reason if it cannot complete, and fix every verified finding before rerunning affected checks and review. Only the clean, tested SHA may be pushed to the fork. Use `--force-with-lease` if updating the existing fork branch requires history replacement, then resolve the remote ref again and make the handoff SHA, base SHA, diffstat, and owner-run `gh pr create` command match it exactly.

No task may push to, open a pull request on, comment on, or merge anything in `mcnaveen/health-connect-webhook`. The owner alone may run the documented upstream pull-request command after reviewing the result. The obsolete mock-payload submission is not bundled into this branch; its remaining fork artifacts are handled separately so they cannot distract from or block review of the catch-up fix.

## Ground rules
This spec is implemented by an automated pass running unattended. **There is no approval step and nothing is waiting for one** — do not look for a tick, a marker, or a sign-off anywhere, and do not wait for one.

Tick the boxes in this file as the work is completed; they are the record of progress, and the pipeline reads them to decide whether the change is finished.

Out of scope, deliberately: do NOT mark the pull request ready for review and do NOT call a forge merge API. Implementation marks the pull request ready only after the task list is complete. Afterward Completion may ask the Store to perform Automatic Merge only when the planner and final implementation agent authorized the exact result. Leave the pull request in a state worth reading.

### Task 1: Rebuild the candidate on the live upstream baseline

- [x] Fetch `origin` and `upstream`, resolve their branch tips, and record the current `upstream/main`, `origin/feat/offline-catchup-sync-upstream`, and local `feat/offline-catchup-sync-upstream` SHAs rather than trusting the stale handoff values.
- [x] Recreate the catch-up candidate from the fetched `upstream/main`, carrying forward the valid behavior from the two-commit remote history ending at `e06ee92` while preserving the single `SyncManager` companion object and the removal of the unrelated `NoMatchingData` per-type cursor advancement.
- [x] Resolve `SyncManager.kt` against upstream's `WebhookDeliveryFormat.JSON` and `WebhookDeliveryFormat.GRPC` branches so catch-up slices use the same current delivery, filtering, payload-building, retry, and logging behavior as ordinary syncs.
- [x] Preserve the existing `SyncWorker.kt` and `SyncForegroundService.kt` automatic entry points, `HealthConnectManager.LOOKBACK_HOURS` coupling, 48-hour threshold, 24-hour slicing, 30-day clamp, and rate-limit paths.
- [x] Keep the upstream-ready history small and conventional, with a complete buildable `feat:` commit followed by its JUnit test commit; do not retain a corrective commit whose parent fails to compile.
- [x] Mark completed

### Task 2: Isolate scheduled catch-up progress

- [x] Add a dedicated automatic/scheduled-sync progress key plus nullable-long getter and setter to `app/src/main/java/com/hcwebhook/app/PreferencesManager.kt`, matching the surrounding `KEY_LAST_SYNC_TIME`, `getLastSyncTime`, and `setLastSyncTime` conventions without changing their existing UI/API meaning.
- [x] Change `SyncManager.performSyncWithCatchUp` to resolve replay from the dedicated cursor, using the legacy general timestamp only as a one-time seed when the new key is absent and persisting that seed independently.
- [x] On the normal no-catch-up path, update automatic progress only after `performSync` returns success; on the replay path, checkpoint it after each successful slice and leave it unchanged for the failed slice and all later slices.
- [x] Keep manual calls from `ManualSyncCard.kt` and API calls from `LocalTcpServerManager.handleSyncRequest` on `performSync`, so they may update `KEY_LAST_SYNC_TIME` and per-type record cursors but cannot move the new automatic progress cursor.
- [x] Preserve useful last-sync UI and local API reporting: normal automatic sync may continue updating the general timestamp through `performSync`, while a completed replay updates the general timestamp once without using it as future catch-up state.
- [x] Update comments and the descriptions in `README.md`, `docs/webhook.md`, and `docs/local-http.md` to distinguish automatic catch-up progress from manual/API last-sync activity.
- [x] Mark completed

### Task 3: Add regression coverage and run the real build gates

- [x] Extend `app/src/test/java/com/hcwebhook/app/SyncManagerCatchUpTest.kt` using its existing JUnit 4 style, extracting a small pure watermark-selection helper if needed to test preference selection without Android framework mocks.
- [x] Add a regression case in which an old stored automatic cursor wins over a newer general timestamp representing a successful one-day manual sync, and cover the equivalent API behavior through the same state boundary.
- [x] Cover first-use migration from the legacy timestamp, absence of both timestamps, successful normal-run initialization, successful per-slice checkpointing, and failure leaving automatic progress at the last completed boundary.
- [x] Retain the existing threshold, future-watermark, slice-contiguity, final-boundary, and 30-day-clamp cases.
- [x] Run `./gradlew assembleDebug` on the feature commit so every retained commit is buildable, then run `./gradlew assembleDebug`, `./gradlew test`, and `./gradlew lint` on the final candidate head; fix failures rather than substituting static inspection or an untested handoff disclaimer.
- [x] Mark completed

### Task 4: Review the complete candidate and refresh its handoff

- [x] Verify the diff against the fetched `upstream/main` contains only the catch-up implementation, tests, and related documentation; specifically exclude `AndroidManifest.xml`, `network_security_config.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `MockPayloadBuilder.kt`, `docs/specs/`, and `docs/upstream-pr/` from the upstream-ready branch.
- [x] Update `docs/upstream-pr/offline-catchup-sync.md` to follow `.github/PULL_REQUEST_TEMPLATE.md`, describe the dedicated automatic-progress cursor and migration trade-off, preserve the prior-art result, and report the Gradle results truthfully.
- [x] Update the catch-up section of `docs/upstream-pr/README.md` with the candidate's exact base SHA, head SHA, commit list, diffstat, tests, and the owner-only command using `--head ya-breeze:feat/offline-catchup-sync-upstream --base main`.
- [x] Run the full Codex Review Gate against the pinned candidate diff and the associated handoff/spec scope, including correctness, standards, and spec/tests passes plus one independent Claude peer attempt when available; compare working-tree status before and after the peer review.
- [x] Verify every finding against the pinned diff, fix all valid findings, rerun affected Gradle checks, refresh the recorded SHA and docs, and repeat the native gate until no unaddressed finding remains; record the independent review as reviewed or peer unavailable without treating an unavailable peer as a native-review substitute.
- [x] Mark completed

### Task 5: Publish and prove the exact fork ref

- [x] Immediately before publishing, fetch the existing fork ref and update only `origin/feat/offline-catchup-sync-upstream`, using `--force-with-lease` if the rebuilt history requires replacement; do not push any ref to `upstream`.
- [x] Resolve `refs/heads/feat/offline-catchup-sync-upstream` from `origin` after the push and prove that its tip equals the exact SHA that passed Gradle and the final Review Gate, that its merge base equals the documented current upstream base, and that its commit list matches the reviewed history.
- [x] Recheck `docs/upstream-pr/README.md` and `docs/upstream-pr/offline-catchup-sync.md` against the resolved remote ref, leaving no old `777ff1d`, `0032356`, or `e06ee92` claim unless it is explicitly identified as historical context.
- [x] Remove the obsolete mock-payload command from the ready-to-run handoff and state that current upstream already emits integer `measurement_location`; leave detailed cleanup of that separate submission to the deferred follow-up.
- [x] State plainly that all three Gradle commands and the native Review Gate passed, report the independent peer status, and confirm that nothing was opened, merged, pushed, or commented on in `mcnaveen/health-connect-webhook`.
- [x] Mark completed
