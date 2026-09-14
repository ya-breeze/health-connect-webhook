# Archive the superseded mock-payload submission
Idea: ya-breeze/idea-forge#420

## Why

The fork still has `fix/mock-payload-measurement-location` at `db725ec9ee2793bd40e38a37d8a286080bbd3b1a`, a one-line candidate based on `777ff1d6ede736a982506e220635f96d78767cc5` that replaced the mock skin-temperature value `"wrist"` with integer `3`. That candidate is obsolete. Upstream commit `1f903824ea05a111fecb34689b1bdb7c64614dcb` (`✨ feat(webhook): enhance gRPC and JSON delivery with new logging and filtering options`) subsequently rebuilt `MockPayloadBuilder` around `buildHealthData` and began serializing `skin.measurementLocation` directly. The commit is now in the ancestry of the fetched `upstream/main` at `7555b53b8fa6eb3ea1bad5ae83cdfc909fbe459e`, and its integer output agrees with `SyncManager.buildJsonPayload` and the `number (integer)` contract in `docs/webhook.md`.

The catch-up handoff currently says the mock-payload submission is obsolete, but it deleted `docs/upstream-pr/mock-payload-measurement-location.md` and removed the corresponding evidence from `docs/upstream-pr/prior-art.md`. That leaves no durable record of which upstream change superseded the candidate, while the fork branch still exists. Recording the resolved outcome now prevents the old branch from being mistaken for a pull-request candidate and completes the follow-up deliberately separated from the catch-up correction.

## How

Restore `docs/upstream-pr/mock-payload-measurement-location.md` as a concise archival outcome record rather than a pull-request body. It will identify both the obsolete fork commit and the exact upstream commit that superseded it, explain how the upstream implementation now derives the integer from `SkinTemperatureData.measurementLocation`, and state unambiguously that no upstream pull request should be opened from the old branch. Restore the mock-payload section of `docs/upstream-pr/prior-art.md`, retaining its earlier search findings but changing the verdict from “no existing report” to “resolved independently upstream.” Update the obsolete-submission section in `docs/upstream-pr/README.md` to link the archival record and label `fix/mock-payload-measurement-location` as retained only for history.

Keeping the remote branch preserves provenance and avoids destructive cleanup, at the cost of leaving an obsolete ref visible; the documentation must therefore make its archival status unmistakable and must contain no ready-to-run pull-request command for it. This documentation-only change does not alter `MockPayloadBuilder.kt`, `SyncManager.kt`, `docs/webhook.md`, or any upstream-ready catch-up code. It must not delete or rewrite a local or remote branch, push changes to `mcnaveen/health-connect-webhook`, open an upstream pull request, or modify the existing decisions concerning dependency commit `299e41e0` and cleartext-network commit `07030b59`.

### Task 1: Record the upstream resolution

- [ ] Recreate `docs/upstream-pr/mock-payload-measurement-location.md` as an archival outcome record, replacing the former pull-request-template framing with the original mismatch, obsolete fork commit `db725ec9ee2793bd40e38a37d8a286080bbd3b1a`, and superseding upstream commit `1f903824ea05a111fecb34689b1bdb7c64614dcb`.
- [ ] Explain that upstream commit `1f903824ea05a111fecb34689b1bdb7c64614dcb` changed `MockPayloadBuilder.build` to serialize `skin.measurementLocation` from the shared mock `HealthData`, producing an integer consistent with `SyncManager.buildJsonPayload` and `docs/webhook.md`.
- [ ] State prominently that `fix/mock-payload-measurement-location` is obsolete and archival, is not an upstream pull-request candidate, and must not be used to open a pull request; omit the former PR checklist, placeholder issue links, and `gh pr create` command.
- [ ] Preserve the repository’s agent-authored documentation convention by ending the restored record with `Created by Codex`.
- [ ] Update `docs/upstream-pr/prior-art.md` with a dedicated mock-payload section that retains the earlier issue/PR search evidence, records the later upstream resolution, links or names the full superseding commit, and changes the verdict to resolved independently upstream rather than still awaiting submission.
- [ ] Mark completed

### Task 2: Make the handoff consistently archival

- [ ] Update the `Obsolete mock-payload submission` section of `docs/upstream-pr/README.md` to link `mock-payload-measurement-location.md`, name the superseding upstream commit, and identify `fix/mock-payload-measurement-location` at `db725ec9ee2793bd40e38a37d8a286080bbd3b1a` as a retained historical branch rather than an actionable candidate.
- [ ] Ensure the three upstream-handoff documents consistently say that no mock-payload pull request should be opened and contain no command or checklist that presents the archival branch as ready for submission.
- [ ] Leave the catch-up candidate instructions and the existing `299e41e0` dependency and `07030b59` cleartext-network exclusion rationale unchanged except for any minimal wording needed to keep headings or cross-links accurate.
- [ ] Confirm the change is limited to `docs/upstream-pr/README.md`, `docs/upstream-pr/mock-payload-measurement-location.md`, and `docs/upstream-pr/prior-art.md`; do not change application code or local/remote branch refs.
- [ ] Mark completed
