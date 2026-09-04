# Upstream-ready pull request for the fork's offline catch-up sync
Idea: ya-breeze/idea-forge#183

## Why

This repository is `ya-breeze/health-connect-webhook`, a fork of `mcnaveen/health-connect-webhook`. The fork carries local work that upstream does not have, and that work is currently stranded: it lives only on fork branches, it is not rebased onto current upstream `main`, and nobody upstream can review it.

The owner located the work on the issue. Two fork branches share the same commits, so there is one line of work, not two. `feat/offline-catchup-sync` holds three commits — `c85c2c39` (replay missed data after extended offline periods), `df941326` (catch-up correctness and reliability fixes from review), and `3d75519a` (unit tests for catch-up slice planning). `feat/update-dependencies` starts with those same three SHAs and adds three more — `299e41e0` (dependency bump to Kotlin 2.1.21 and BOM 2025.09), `07030b59` (allow cleartext HTTP traffic), and `3e85b813` (use an integer for `measurement_location` in the mock payload). Both branches were 26 commits behind upstream when the owner measured them, so the diff a reviewer would see today is not the diff the branches currently show.

The feature addresses a limitation upstream documents rather than hides. `README.md` lists "🕒 **48-Hour Lookback**" under Known Limitations and says data older than 48 hours may not sync if the app was not running. The constant behind it is `LOOKBACK_HOURS = 48L` in the `HealthConnectManager` companion object, applied in `readHealthData` when no explicit range is given (`app/src/main/java/com/hcwebhook/app/HealthConnectManager.kt:357`). Because upstream states the limit deliberately, the pull request has to argue for changing it, and the idea explicitly asks whether the same change was already proposed and rejected. That investigation is part of the deliverable, not a precondition for it.

One of the side commits is a genuine upstream bug that still exists on current `main`. `MockPayloadBuilder.kt:144` emits `put("measurement_location", "wrist")` as a string, while the real payload emits an integer (`SyncManager.kt:624`, from `SkinTemperatureData.measurementLocation: Int`) and `docs/webhook.md:154` documents the field as `number (integer)`. The mock payload therefore contradicts both the code and the published spec. That is worth sending upstream — on its own, not folded into a sync feature.

Upstream is a third party's public repository. Opening a pull request there is not an unattended action, so this change stops at a pushed branch on the fork plus a written pull request body that the owner submits by hand.

## How

Produce upstream-ready branches on the fork and the text to accompany them. Write nothing to `mcnaveen/health-connect-webhook` — no push, no pull request, no issue, no comment. That boundary is absolute and applies to every task below.

Rebase, do not merge. Create the upstream-ready branch directly on `upstream/main` and cherry-pick the three catch-up commits onto it, so the branch contains only the feature and reviewers see the diff they would actually merge. Expect conflicts, because upstream moved 26 commits: `readHealthData` gained a `dataTypeResolutions` parameter and per-type resolution helpers, `readAllRecords` gained inter-page throttling via `READ_PAGE_DELAY_MS`, `withHealthConnectRetry` gained rate-limit backoff via `RATE_LIMIT_MAX_ATTEMPTS`, `RATE_LIMIT_INITIAL_DELAY_MS` and `RATE_LIMIT_MAX_DELAY_MS`, and `SyncManager` gained `clampedMaxEndMs` for watermark clamping plus a per-webhook `dataTypeFilter` path. A catch-up feature that slices a long window into many reads interacts directly with that rate-limit work, so resolve the conflicts by using upstream's throttling rather than around it.

Split the work into three upstream submissions and one exclusion:

- **Catch-up sync** — the main pull request, built from the three catch-up commits.
- **Mock payload `measurement_location`** — a separate branch and separate pull request body. It is a small, self-contained bug fix against a documented contract, and bundling it into a feature would hide it.
- **Dependency bump (`299e41e0`)** — excluded. A Kotlin and Compose BOM upgrade belongs to the maintainer's own release schedule, touches build configuration that CI signs and publishes, and fixes nothing a user reports. It stays on the fork.
- **Cleartext HTTP (`07030b59`)** — excluded. It relaxes network security so the app can reach a LAN host over plain HTTP, which is a local deployment need. Pushing a security relaxation into a public project on this deployment's behalf is not acceptable, and if an upstream-general case exists it needs its own pull request with its own justification.

Match upstream's conventions rather than the fork's habits. Commit messages follow the conventional prefixes shown in `README.md` (`feat:`, `fix:`, `test:`). Unit tests go under `app/src/test/java/com/hcwebhook/app/` using plain JUnit 4, as `DashboardFormatterTest` and `ReleaseNotesFormatterTest` do — that is the only test style the project's `testImplementation(libs.junit)` supports. Pull request bodies follow `.github/PULL_REQUEST_TEMPLATE.md` section for section. Upstream keeps `docs/webhook.md` and `docs/local-http.md` in sync with app behaviour and states so in `README.md`, so any change to the 48-hour window updates those documents and the Known Limitations entry in the same commit.

CI (`.github/workflows/ci.yml`) runs `./gradlew assembleDebug`, `./gradlew test`, and `./gradlew lint`. It triggers only on `main`, `master`, and `develop` and on pull requests targeting them, so pushing a feature branch to the fork produces no CI signal. Run the three Gradle commands locally when the Android SDK is available. When it is not, say so plainly in the handoff and name what was verified instead. Do not claim a gate ran when it did not, and do not open a fork-internal pull request just to trigger CI.

Deliverables land on the idea branch under `docs/upstream-pr/`: the prior-art findings, one pull request body per submission, and a handoff note. The upstream-ready branches themselves stay clean — they carry the feature commits only, with no spec file, no `docs/upstream-pr/` content, and no other idea-forge artifact.

Excluded from this change: opening or updating anything on `mcnaveen/health-connect-webhook`; upgrading dependencies upstream; upstreaming the cleartext HTTP change; and deleting or rewriting the fork branches, which stay as they are so the deployment keeps building from them.

## Ground rules
This spec is implemented by an automated pass running unattended. **There is no approval step and nothing is waiting for one** — do not look for a tick, a marker, or a sign-off anywhere, and do not wait for one.

Tick the boxes in this file as the work is completed; they are the record of progress, and the pipeline reads them to decide whether the change is finished.

Out of scope, deliberately: do NOT mark the pull request ready for review and do NOT merge it. Those are the pipeline's own final steps, run once the task list is complete. The operator reviews the pull request and merges it themselves; that is the only gate this work passes through, so leave it in a state worth reading.

### Task 1: Establish the real upstream baseline

- [ ] Add an `upstream` remote pointing at `https://github.com/mcnaveen/health-connect-webhook.git` and fetch it. Read the GitHub token from `/data/data.json` if authentication is needed.
- [ ] Fetch `origin` and confirm that `origin/feat/offline-catchup-sync` and `origin/feat/update-dependencies` exist; the local clone currently packs only `origin/main`.
- [ ] Record the current ahead/behind counts of `origin/main`, `origin/feat/offline-catchup-sync`, and `origin/feat/update-dependencies` against `upstream/main`. Report the measured numbers rather than the numbers quoted in the idea, which are now stale.
- [ ] Confirm that `feat/offline-catchup-sync` contains exactly `c85c2c39`, `df941326`, and `3d75519a`, and that `feat/update-dependencies` adds `299e41e0`, `07030b59`, and `3e85b813` on top. If the SHAs no longer resolve, find the equivalent commits by subject and record the new SHAs.
- [ ] Read the three catch-up commits in full and write a short description of what they change: which files, which functions, which preference keys, and whether the feature is opt-in or on by default.
- [ ] Mark completed

### Task 2: Investigate whether upstream already rejected this

- [ ] Search upstream issues and pull requests, open and closed, for prior proposals. Cover at least: `catch-up`, `catchup`, `backfill`, `offline`, `missed data`, `48 hour`, `48h`, `lookback`, `history`, and `READ_HEALTH_DATA_HISTORY`.
- [ ] For every plausible match, record the number, title, state, author, and — when it was closed without merging — the maintainer's stated reason, quoted verbatim with a link.
- [ ] Search separately for prior reports of the `measurement_location` mock payload mismatch, so the second pull request does not duplicate an existing one.
- [ ] Write `docs/upstream-pr/prior-art.md` with a table of searches run, hits found, and the verdict for each. State explicitly when a search found nothing; an empty result is a finding and must be visible.
- [ ] If a maintainer previously rejected an equivalent proposal, state that at the top of the file, explain whether the rebased branch answers the objection, and carry that conclusion into the pull request body in Task 5.
- [ ] Mark completed

### Task 3: Build the upstream-ready catch-up branch

- [ ] Create `feat/offline-catchup-sync-upstream` from `upstream/main`.
- [ ] Cherry-pick `c85c2c39`, `df941326`, and `3d75519a` onto it in that order.
- [ ] Resolve conflicts against current upstream code, specifically: the `dataTypeResolutions` parameter and per-type resolution helpers in `HealthConnectManager.readHealthData`; inter-page throttling in `readAllRecords` (`READ_PAGE_DELAY_MS`, `PAGINATED_READ_THROTTLE_TYPES`); rate-limit backoff in `withHealthConnectRetry`; `clampedMaxEndMs` and the per-webhook `dataTypeFilter` path in `SyncManager.performSync`.
- [ ] Make catch-up window slicing go through upstream's existing retry and throttle paths instead of adding a second mechanism. A multi-day replay issues many more Health Connect reads than a 48-hour sync, and upstream added that throttling for exactly this failure mode.
- [ ] Keep any new preference keys consistent with `PreferencesManager`: a `private const val KEY_…` in the companion object plus paired getter and setter, matching the surrounding style.
- [ ] Update `README.md` (the Known Limitations 48-hour entry and the webhook format paragraph), `docs/webhook.md`, and `docs/local-http.md` wherever the branch changes the described window or watermark behaviour.
- [ ] Reshape the history into a small number of conventional commits — a `feat:` for the feature and a `test:` for the unit tests — with messages written for an upstream reader who has no context on this fork.
- [ ] Verify the branch excludes the cleartext and dependency changes: `AndroidManifest.xml`, `network_security_config`, `gradle/libs.versions.toml`, and `app/build.gradle.kts` must not appear in the diff against `upstream/main` unless the feature genuinely requires them, and `MockPayloadBuilder.kt` must not appear at all.
- [ ] Confirm the branch carries no idea-forge artifact: no `docs/specs/` file and no `docs/upstream-pr/` file.
- [ ] Push the branch to `origin` only.
- [ ] Mark completed

### Task 4: Gate the branch

- [ ] Run `./gradlew assembleDebug`, `./gradlew test`, and `./gradlew lint` on the branch, mirroring `.github/workflows/ci.yml`.
- [ ] If the Android SDK is unavailable and the commands cannot run, record that fact verbatim in the handoff, name what was verified instead, and do not describe the branch as CI-clean.
- [ ] Confirm the catch-up unit tests sit under `app/src/test/java/com/hcwebhook/app/`, use JUnit 4 imports (`org.junit.Test`, `org.junit.Assert`), and follow the naming style of `DashboardFormatterTest`.
- [ ] Confirm the tests cover slice planning at its edges: no prior watermark, a watermark inside the normal window, a watermark days old, and a watermark in the future.
- [ ] Mark completed

### Task 5: Write the catch-up pull request body

- [ ] Write `docs/upstream-pr/offline-catchup-sync.md` following `.github/PULL_REQUEST_TEMPLATE.md` section for section, in the template's order.
- [ ] In Summary, state the user-visible problem (data lost when the device is offline or the app is not running longer than the documented 48-hour window), the approach, and whether the behaviour is opt-in or default.
- [ ] Explain the interaction with Health Connect rate limits and how the branch stays inside upstream's existing throttle and retry logic.
- [ ] Add a prior-art paragraph summarising Task 2, linking any related upstream issue or pull request, and answering any earlier objection directly.
- [ ] Fill the Type of change and Checklist sections truthfully, reflecting the gates that actually ran in Task 4.
- [ ] List the documentation files the branch updates, so the maintainer can see the specs stayed in sync.
- [ ] End with the authorship marker `Created by Claude`, as this environment requires for agent-authored GitHub content.
- [ ] Mark completed

### Task 6: Handle the side commits separately

- [ ] Re-verify the mock payload bug on current `upstream/main`: `MockPayloadBuilder.kt:144` emits a string while `SyncManager.kt:624` emits `Int` and `docs/webhook.md:154` documents `number (integer)`. If upstream has already fixed it, drop this submission and say so.
- [ ] Create `fix/mock-payload-measurement-location` from `upstream/main` containing only that one-line fix, with a `fix:` commit message, and push it to `origin`.
- [ ] Write `docs/upstream-pr/mock-payload-measurement-location.md` using the same template, citing the three file locations as evidence, and end it with `Created by Claude`.
- [ ] Record in `docs/upstream-pr/prior-art.md` why `299e41e0` (dependency bump) and `07030b59` (cleartext HTTP) are not being sent upstream, in one short paragraph each.
- [ ] Leave `feat/update-dependencies` and `feat/offline-catchup-sync` on the fork untouched, so the deployment keeps building from them.
- [ ] Mark completed

### Task 7: Hand off to the owner

- [ ] Write `docs/upstream-pr/README.md` listing each upstream-ready branch, its head SHA, its base SHA on `upstream/main`, its pull request body file, and its diffstat.
- [ ] Include the exact `gh pr create --repo mcnaveen/health-connect-webhook --head ya-breeze:<branch> --base main --body-file <path>` command for each submission, clearly marked as a command for the owner to run. Do not run it.
- [ ] State plainly which gates ran and which did not, and repeat that nothing was written to `mcnaveen/health-connect-webhook`.
- [ ] Confirm every unticked box in this spec is now resolved, and that `docs/upstream-pr/` contains the prior-art file, both pull request bodies, and this handoff note.
- [ ] Mark completed
