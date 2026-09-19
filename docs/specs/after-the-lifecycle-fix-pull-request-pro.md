# Assemble and review the combined upstream catch-up candidate
Idea: ya-breeze/idea-forge#599

## Why

The corrected automatic catch-up data path exists as `feat/offline-catchup-sync-corrected` commit `478fd2904fb8d0d64fb2a60b160c534c4ee5bbaf`, whose parent is the preserved upstream base `7555b53b8fa6eb3ea1bad5ae83cdfc909fbe459e`. The lifecycle prerequisite represented by `feature/idea-482-after-the-data-integrity-change-lands-co` at `ded652177eeb3f5056d5c65e29f46550632a8746` changes that candidate’s `SyncForegroundService.kt` and adds `SyncForegroundServiceLifecycleTest.kt`, but the idea branch also carries `docs/specs/`, `docs/upstream-pr/`, and task-checkpoint history that does not belong in an upstream submission.

The published fork branch `origin/feat/offline-catchup-sync-upstream` still points at `8510dddd5dca98ce6c3f83a2c6b5fd69259772b6`, while the older `754389566f546c8c520852354cda3db55e57b47c` also appears in historical handoff material. Neither is the combined corrected result. Before review remediation, the upstream-only combined tree spans 22 files, including the catch-up implementation, its user documentation and tests, localization repair, and the two lifecycle files. Earlier validation and review claims cannot establish the integrity of that new tree: the exact candidate eventually published must have one pinned SHA, a compact upstream-only history, all three Gradle gates, and a fresh Codex Review Gate.

## How

Create a durable local candidate ref in a fresh clean worktree rather than rewriting the stale `feat/offline-catchup-sync-upstream` worktree. Start from the exact upstream base, retain `478fd2904fb8d0d64fb2a60b160c534c4ee5bbaf` as the first candidate commit, and derive the lifecycle addition deterministically from the path-limited difference between that commit and `ded652177eeb3f5056d5c65e29f46550632a8746` for `app/src/main/java/com/hcwebhook/app/SyncForegroundService.kt` and `app/src/test/java/com/hcwebhook/app/SyncForegroundServiceLifecycleTest.kt`. Commit that net lifecycle result as one integration commit and verify its two resulting blobs match the accepted prerequisite tree. This preserves the production entry points—`SyncManager.performSyncWithCatchUp`, `SyncForegroundService.onStartCommand`, `onTimeout`, and `onDestroy`—while the extracted automatic-sync helpers and production-used `SyncForegroundServiceLifecycleCoordinator` provide deterministic JVM coverage, without replaying fork-only specifications or checkpoint commits.

Treat each candidate SHA as immutable evidence. Validate it in a separate detached clean worktree, inspect Gradle’s test-result files for both `foss` and `playstore` variants, and run a fresh Codex Review Gate over the exact base-to-head range. Fix every verified finding on the mutable candidate ref, fold remediation into the integration commit so the final history remains compact while the original corrected-data commit remains intact, repin the SHA, and repeat the affected and complete gates plus review. Record only a clean final SHA for which validation and review agree.

This part deliberately stops before remote publication. It must not push or otherwise update `origin/feat/offline-catchup-sync-upstream`, and it must not edit any existing GitHub pull request. It will record reviewed local evidence and the ordered owner-only publication procedure in `docs/upstream-pr/README.md`, while keeping `docs/upstream-pr/offline-catchup-sync.md` suitable for verbatim use as the public upstream pull-request body. Updating the fork branch and the existing `ya-breeze/health-connect-webhook` pull-request description is deferred until the lifecycle prerequisite and this reviewed-candidate change have landed. The command `gh pr create --repo mcnaveen/health-connect-webhook --head ya-breeze:feat/offline-catchup-sync-upstream --base main --body-file docs/upstream-pr/offline-catchup-sync.md` remains owner-only documentation and must not be executed. No part of this work may create, comment on, review, or merge an issue or pull request in `mcnaveen/health-connect-webhook`.

## Validation Commands

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

## Ground rules
This spec is implemented by an automated pass running unattended. **There is no approval step and nothing is waiting for one** — do not look for a tick, a marker, or a sign-off anywhere, and do not wait for one.

Tick the boxes in this file as the work is completed; they are the record of progress, and the pipeline reads them to decide whether the change is finished.

Out of scope, deliberately: do NOT mark the pull request ready for review and do NOT call a forge merge API. Implementation marks the pull request ready only after the task list is complete. Afterward Completion may ask the Store to perform Automatic Merge only when the planner and final implementation agent authorized the exact result. Leave the pull request in a state worth reading.

### Task 1: Assemble the local candidate on the preserved base

- [x] Verify that `478fd2904fb8d0d64fb2a60b160c534c4ee5bbaf` has parent `7555b53b8fa6eb3ea1bad5ae83cdfc909fbe459e`, and verify the two lifecycle source blobs at `ded652177eeb3f5056d5c65e29f46550632a8746` before changing any candidate ref.
- [x] Create a fresh clean worktree and durable local ref for the reviewed candidate from `7555b53b8fa6eb3ea1bad5ae83cdfc909fbe459e`; leave the existing stale candidate worktree, uncommitted user work, and both remotes untouched.
- [x] Retain `478fd2904fb8d0d64fb2a60b160c534c4ee5bbaf` as the first candidate commit, then apply only the path-limited lifecycle difference for `SyncForegroundService.kt` and `SyncForegroundServiceLifecycleTest.kt` as one focused integration commit.
- [x] Verify that the resulting lifecycle file blobs exactly match `ded652177eeb3f5056d5c65e29f46550632a8746` and preserve duplicate-start coalescing, retained schedule-ID rescheduling, generation-safe completion, timeout cancellation, destruction behavior, and alarm-reschedule failure isolation.
- [x] Mark completed

### Task 2: Prove the history and diff are upstream-only

- [x] Verify that the candidate merge base is exactly `7555b53b8fa6eb3ea1bad5ae83cdfc909fbe459e`, that `478fd2904fb8d0d64fb2a60b160c534c4ee5bbaf` is the first candidate commit with that parent, and that the candidate head initially has `478fd2904fb8d0d64fb2a60b160c534c4ee5bbaf` as its parent.
- [x] Compare the initial combined tree with `478fd2904fb8d0d64fb2a60b160c534c4ee5bbaf` and confirm that only `app/src/main/java/com/hcwebhook/app/SyncForegroundService.kt` and `app/src/test/java/com/hcwebhook/app/SyncForegroundServiceLifecycleTest.kt` differ.
- [x] Verify that the initial base-to-head path set is exactly the union of the paths changed by `478fd2904fb8d0d64fb2a60b160c534c4ee5bbaf` and the lifecycle test path, with `SyncForegroundService.kt` carrying both candidates’ changes.
- [x] Inspect the complete base-to-head name list and diffstat. Exclude `docs/specs/`, `docs/upstream-pr/`, `AndroidManifest.xml`, `network_security_config.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `MockPayloadBuilder.kt`, dependency upgrades, cleartext policy, and every idea-forge checkpoint artifact.
- [x] Confirm the candidate worktree is clean and capture its exact SHA, complete ordered commit list, and base-to-head diffstat for validation and handoff evidence.
- [x] Mark completed

### Task 3: Validate one pinned candidate SHA

- [x] Check out the captured candidate SHA in a separate clean detached worktree and run `./gradlew assembleDebug`, `./gradlew test`, and `./gradlew lint`, recording every command and outcome against that SHA rather than a mutable branch name.
- [x] Inspect the generated unit-test result files and confirm that `SyncManagerCatchUpTest`, `SyncManagerWebhookDeliveryTest`, and `SyncForegroundServiceLifecycleTest` execute successfully for the configured `foss` and `playstore` application variants.
- [x] Confirm that the lifecycle results cover duplicate starts, timeout, stale completion, destruction, and reschedule failures, and that the data-path results cover automatic cursor ordering, bounded overlap, and production webhook-delivery aggregation.
- [x] If a gate fails, make the narrowest tested correction on the mutable candidate ref, fold it into the integration commit, capture the new SHA, recreate or reset the detached validation worktree, rerun the affected check, and rerun all three complete commands before review.
- [x] Confirm the detached validation worktree is clean and its SHA is unchanged after the successful command set.
- [x] Mark completed

### Task 4: Run the Review Gate against that exact SHA

- [x] Invoke a fresh Codex Review Gate for the exact `7555b53b8fa6eb3ea1bad5ae83cdfc909fbe459e` to pinned-candidate range; do not review the idea branch, a dirty worktree, or either stale candidate head.
- [x] Require the review to cover automatic-cursor ordering and overlap behavior, webhook-delivery aggregation, `SyncForegroundServiceLifecycleCoordinator` race and cleanup invariants, regression coverage, upstream-base fidelity, compact history, and absence of fork-only artifacts.
- [x] Investigate every finding against the pinned tree, fix every verified finding with regression coverage where applicable, and retain concise evidence for any finding demonstrated not to apply.
- [x] Whenever a fix changes the candidate, fold the remediation into the integration commit, capture the new SHA, rerun affected checks plus `./gradlew assembleDebug`, `./gradlew test`, and `./gradlew lint`, and run another fresh Review Gate against the new exact SHA.
- [x] Finish only when the final Review Gate has no unresolved verified findings and its reviewed SHA exactly matches the clean SHA that passed all three complete Gradle commands.
- [x] Mark completed

### Task 5: Record the reviewed local handoff without publishing it

- [x] Refresh `docs/upstream-pr/README.md` on the idea branch with the exact base and final reviewed head SHAs, durable local candidate ref, complete ordered commit list, base-to-head diffstat, successful validation results, final Review Gate result, upstream-only exclusion audit, and an explicit statement that publication remains pending.
- [x] Refresh `docs/upstream-pr/offline-catchup-sync.md` as a public-only pull-request body with the summary, lifecycle description, validation results, and completed checklist, excluding local refs, pending-publication statements, internal review detail, handoff boundaries, and owner commands.
- [x] Record the ordered owner-only publication sequence in `docs/upstream-pr/README.md`: land the handoff on the fork's `main` so the prior-art link resolves, replace the stale fork branch with the exact reviewed candidate under an explicit force-with-lease, verify the remote SHA, and only then run the upstream `gh pr create` command. State that none of these mutations has been performed.
- [x] Verify that these fork-only handoff edits exist only on the idea branch and do not alter the reviewed candidate ref, SHA, history, or upstream diff; do not push the candidate branch or edit the existing fork pull-request description in this part.
- [x] Mark completed
