# Handoff: upstream-ready catch-up pull request

Nothing has been written to `mcnaveen/health-connect-webhook`: no branch, pull request, issue, comment, review, or merge. The candidate exists only on the `ya-breeze/health-connect-webhook` fork. The owner may run the command below after reviewing this handoff.

## Candidate

- Branch: `feat/offline-catchup-sync-upstream`
- Base: `7555b53b8fa6eb3ea1bad5ae83cdfc909fbe459e` (`upstream/main` when fetched)
- Head: `8510dddd5dca98ce6c3f83a2c6b5fd69259772b6`
- Commits, oldest first:
  - `ed64b00f700277b2f5004a9ecae993ced06d6575 feat: replay missed automatic sync data`
  - `8510dddd5dca98ce6c3f83a2c6b5fd69259772b6 test: cover automatic catch-up progress`
- Pull request body: [`offline-catchup-sync.md`](./offline-catchup-sync.md)

Diffstat against the base:

```text
 README.md                                          |   5 +
 .../java/com/hcwebhook/app/HealthConnectManager.kt |   2 +-
 .../com/hcwebhook/app/LocalTcpServerManager.kt     |   6 +-
 .../java/com/hcwebhook/app/PreferencesManager.kt   |  12 +-
 .../com/hcwebhook/app/SyncForegroundService.kt     |   6 +-
 app/src/main/java/com/hcwebhook/app/SyncManager.kt | 208 +++++++++-
 app/src/main/java/com/hcwebhook/app/SyncWorker.kt  |   2 +-
 .../com/hcwebhook/app/components/ManualSyncCard.kt |  15 +-
 app/src/main/res/values-de/strings.xml             |  16 +
 app/src/main/res/values-es/strings.xml             |  16 +
 app/src/main/res/values-fr/strings.xml             |  16 +
 app/src/main/res/values-it/strings.xml             |  16 +
 app/src/main/res/values-ja/strings.xml             |  16 +
 app/src/main/res/values-ko/strings.xml             |  16 +
 app/src/main/res/values-pt/strings.xml             |  16 +
 app/src/main/res/values-ta/strings.xml             |  16 +
 app/src/main/res/values-zh/strings.xml             |  16 +
 .../com/hcwebhook/app/SyncManagerCatchUpTest.kt    | 418 +++++++++++++++++++++
 docs/local-http.md                                 |   7 +-
 docs/webhook.md                                    |   5 +-
 20 files changed, 808 insertions(+), 22 deletions(-)
```

## Validation and review

- `./gradlew assembleDebug` — passed on the feature parent and candidate head.
- `./gradlew test` — passed on the candidate head, including all four flavor/build-type unit-test variants.
- `./gradlew lint` — passed on the candidate head. The fetched upstream base initially failed on 16 untranslated gRPC resource keys; the feature commit supplies those keys for all nine configured locales.
- Codex Review Gate — passed on the pinned candidate for correctness, repository conventions, and spec/test fidelity after fixing missing/future automatic-cursor fallback, read-boundary handling, manual/API per-type cursor suppression, partial multi-webhook checkpointing, detached-helper test coverage, and the final automatic-cursor documentation mismatch found by native passes.
- Independent Claude peer — peer unavailable. The one clean-gate attempt initialized successfully but was rejected before review by the account's weekly rate limit; the candidate and handoff worktrees were unchanged before and after it.

The upstream-ready diff excludes `AndroidManifest.xml`, `network_security_config.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `MockPayloadBuilder.kt`, `docs/specs/`, and `docs/upstream-pr/`.

## Owner-only pull request command

Run only after this idea branch has been merged to the fork's `main`, so the pull request body's prior-art link resolves:

```bash
gh pr create --repo mcnaveen/health-connect-webhook \
  --head ya-breeze:feat/offline-catchup-sync-upstream --base main \
  --body-file docs/upstream-pr/offline-catchup-sync.md
```

## Obsolete mock-payload submission

Do not open the previous mock-payload pull request. Current upstream already emits integer `measurement_location` values from `MockPayloadBuilder.kt`, matching `SyncManager.kt`, `docs/webhook.md`, and the current payload contract. The obsolete pull-request body and ready-to-run command have been removed; cleanup of its separate fork branch remains deferred.

## Excluded fork-only work

- Dependency upgrades remain on the fork's separate branch.
- Cleartext network policy remains fork-specific and is not in this candidate.
- Idea-forge specs and `docs/upstream-pr/` handoff artifacts are not in the upstream-ready branch.

Created by Codex
