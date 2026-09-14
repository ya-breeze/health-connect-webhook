# Archived outcome: mock-payload measurement location

**Status: obsolete and archival. Do not use `fix/mock-payload-measurement-location` to
open an upstream pull request.**

## Original mismatch

The fork candidate at `db725ec9ee2793bd40e38a37d8a286080bbd3b1a` changed
`MockPayloadBuilder.kt` so the mock skin-temperature payload emitted the integer `3`
instead of the string `"wrist"` for `measurement_location`.

The original mismatch was real: the mock emitted a string, while the real JSON path in
`SyncManager.kt` emitted `skin.measurementLocation` as an integer from
`SkinTemperatureData`, and `docs/webhook.md` documents the field as `number (integer)`.

## Upstream resolution

Upstream commit [`1f903824ea05a111fecb34689b1bdb7c64614dcb`](https://github.com/mcnaveen/health-connect-webhook/commit/1f903824ea05a111fecb34689b1bdb7c64614dcb)
superseded the candidate. It rebuilt `MockPayloadBuilder` so `build` serializes
`skin.measurementLocation` from the shared mock `HealthData`, whose
`SkinTemperatureData.measurementLocation` is the integer `3`. The mock JSON now follows
the same integer-producing model as `SyncManager.buildJsonPayload` and the published
`docs/webhook.md` contract.

## Disposition

`fix/mock-payload-measurement-location` is obsolete and retained only for historical
provenance. It is not an upstream pull-request candidate, and no mock-payload pull request
should be opened from that branch. The branch and its commit are left untouched so the
superseded candidate remains traceable.

Created by Codex
