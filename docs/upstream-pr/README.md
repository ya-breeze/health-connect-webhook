# Handoff: upstream-ready pull requests for `mcnaveen/health-connect-webhook`

Nothing has been written to `mcnaveen/health-connect-webhook` — no push, no pull request, no
issue, no comment. Both branches below live only on this fork
(`ya-breeze/health-connect-webhook`). Opening the pull requests on upstream is the owner's step;
the commands below are ready to run by hand.

## Branch 1 — Catch-up sync (the main submission)

- Branch: `feat/offline-catchup-sync-upstream`
- Base on `upstream/main`: `777ff1d6ede736a982506e220635f96d78767cc5`
- Head: `d0775f46cbc59a3c3534ea57bbc9011e4812a069`
- Pull request body: [`offline-catchup-sync.md`](./offline-catchup-sync.md)
- Diffstat vs. `upstream/main`:
  ```
   README.md                                          |   4 +-
   .../java/com/hcwebhook/app/HealthConnectManager.kt |   2 +-
   .../com/hcwebhook/app/SyncForegroundService.kt     |   6 +-
   app/src/main/java/com/hcwebhook/app/SyncManager.kt | 97 ++++++++++++-
   app/src/main/java/com/hcwebhook/app/SyncWorker.kt  |   2 +-
   .../com/hcwebhook/app/SyncManagerCatchUpTest.kt    | 152 +++++++++++++++++++++
   docs/local-http.md                                 |   2 +-
   docs/webhook.md                                    |   3 +
   8 files changed, 256 insertions(+), 12 deletions(-)
  ```

Command to open the pull request:

```bash
gh pr create --repo mcnaveen/health-connect-webhook \
  --head ya-breeze:feat/offline-catchup-sync-upstream --base main \
  --body-file docs/upstream-pr/offline-catchup-sync.md
```

## Branch 2 — Mock payload `measurement_location` fix

- Branch: `fix/mock-payload-measurement-location`
- Base on `upstream/main`: `777ff1d6ede736a982506e220635f96d78767cc5`
- Head: `db725ec9ee2793bd40e38a37d8a286080bbd3b1a`
- Pull request body: [`mock-payload-measurement-location.md`](./mock-payload-measurement-location.md)
- Diffstat vs. `upstream/main`:
  ```
   app/src/main/java/com/hcwebhook/app/MockPayloadBuilder.kt | 2 +-
   1 file changed, 1 insertion(+), 1 deletion(-)
  ```

Command to open the pull request:

```bash
gh pr create --repo mcnaveen/health-connect-webhook \
  --head ya-breeze:fix/mock-payload-measurement-location --base main \
  --body-file docs/upstream-pr/mock-payload-measurement-location.md
```

## What was excluded, and why

- **`299e41e0` (dependency bump: Kotlin 2.1.21, Compose BOM 2025.09)** — stays on the fork's
  `feat/update-dependencies` branch. Belongs to the maintainer's own release schedule; not sent
  upstream. Detail: [`prior-art.md`](./prior-art.md#why-the-other-two-side-commits-are-not-going-upstream).
- **`07030b59` (allow cleartext HTTP traffic)** — stays on the fork's `feat/update-dependencies`
  branch. A security relaxation for this deployment's LAN target, not something to push into a
  public project on this deployment's behalf. Same detail link as above.
- `feat/update-dependencies` and `feat/offline-catchup-sync` on the fork were left untouched —
  the deployment keeps building from them.

## Gates: what ran and what didn't

- **`./gradlew assembleDebug`, `./gradlew test`, `./gradlew lint` did not run.** The environment
  that prepared these branches has no Android SDK, no `sdkmanager`, and no `java` on `PATH` —
  confirmed by direct check, not assumed. `.github/workflows/ci.yml` only triggers on `main`,
  `master`, `develop`, and PRs targeting them, so pushing these branches to the fork produced no
  CI signal either.
- **What was verified instead**, all by static review against the actual file contents on
  `upstream/main` and on each branch:
  - The catch-up branch's diff was checked file-by-file to confirm it routes through upstream's
    existing `dataTypeResolutions`, `READ_PAGE_DELAY_MS`/`PAGINATED_READ_THROTTLE_TYPES`, and
    `withHealthConnectRetry` paths rather than adding a second throttle mechanism.
  - The excluded-file list (`AndroidManifest.xml`, `network_security_config`,
    `gradle/libs.versions.toml`, `app/build.gradle.kts`, `MockPayloadBuilder.kt`) was grepped out
    of the catch-up branch's diff against `upstream/main` — confirmed absent.
  - The new test file's package, imports (`org.junit.Test`, `org.junit.Assert.*`), and naming
    style were compared directly against `DashboardFormatterTest.kt`.
  - The mock-payload bug was re-confirmed by reading the three cited file locations on current
    `upstream/main` before writing the one-line fix, rather than trusting the idea's line numbers
    (which had shifted as the file grew).
  - **Neither branch is described as CI-clean anywhere in this handoff or the PR bodies** — the
    PR body checklists mark local testing as not run and ask the maintainer/CI to run the Gradle
    gate before merging.
- Please run the three Gradle commands (or open the PR and let upstream CI run them) before
  merging either branch.

## Spec status

Every box in `docs/specs/prepare-a-proper-pr-for-forked-android-a.md` is now ticked. This
directory contains exactly: `prior-art.md`, `offline-catchup-sync.md`,
`mock-payload-measurement-location.md`, and this handoff note.

Created by Claude
