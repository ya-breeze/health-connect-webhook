# Handoff: upstream-ready catch-up pull request

Nothing has been written to `mcnaveen/health-connect-webhook`: no branch, pull request, issue, comment, review, or merge. The candidate exists only locally in the `ya-breeze/health-connect-webhook` fork checkout. The owner may run the command below once a corrected candidate has passed final review — **not yet true of anything described in this file**, see the status below.

## Candidate

- Branch: `feat/offline-catchup-sync-upstream`
- Base: `7555b53b8fa6eb3ea1bad5ae83cdfc909fbe459e` (`upstream/main` when fetched)
- Previous head: `8510dddd5dca98ce6c3f83a2c6b5fd69259772b6` — superseded. It left a crash-unsafe ordering between the automatic and general cursor writes, gave automatic reads no overlap against late or backdated records, and covered mixed webhook success/failure only through a detached boolean helper rather than the real delivery path.
- A corrected candidate exists **only as a local, unpublished branch** (`feat/offline-catchup-sync-corrected`) built from the same base. It fixes the three defects above. It has not been reviewed and is not the final upstream candidate — see [Status](#status).
- Pull request body: [`offline-catchup-sync.md`](./offline-catchup-sync.md)

## Status

- Lifecycle remediation (the `SyncForegroundService` duplicate-start coalescing groundwork this candidate was split from) is still pending.
- Final review of the corrected candidate is still pending. Nothing in this repo has completed the Review Gate against `feat/offline-catchup-sync-corrected`, so no SHA in this handoff should be treated as final or reviewed.
- Until both are done, do not run the pull request command below.

## Validation

`./gradlew assembleDebug`, `./gradlew test` (all flavor/build-type variants), and `./gradlew lint` (all flavors) pass locally on the corrected candidate's tree. This is local build/test evidence only — it is not a substitute for the Review Gate pass recorded in [Status](#status).

The upstream-ready diff excludes `AndroidManifest.xml`, `network_security_config.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `MockPayloadBuilder.kt`, `docs/specs/`, and `docs/upstream-pr/`.

## Owner-only pull request command

Do not run this until [Status](#status) is clear. Once it is, publish the reviewed candidate to `feat/offline-catchup-sync-upstream` on the fork and merge this idea branch to the fork's `main` first, so the pull request body's prior-art link resolves:

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
