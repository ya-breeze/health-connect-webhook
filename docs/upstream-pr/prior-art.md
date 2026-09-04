# Prior art: has upstream already considered this?

No maintainer rejection was found for either submission. Both searches below returned either
nothing on point, or hits that turned out to be a different feature entirely.

## Catch-up / offline sync

Searched `mcnaveen/health-connect-webhook` issues and pull requests (open and closed) for each
of the following terms, including PRs in the results:

| Search term | Hits | Verdict |
|---|---|---|
| `catch-up` | none | No hit. |
| `catchup` | none | No hit. |
| `backfill` | #69 "[Feature] ProtoBuf/gRPC support" (open) | Not related. `backfill` appears once, in the issue's "Impact" line ("Users wishing to backfill past data into an external service..."), about payload transport efficiency (JSON size vs. protobuf), not about recovering missed syncs. |
| `offline` | none | No hit. |
| `missed data` | none | No hit. |
| `48 hour` | #48 "feat: configurable resolution for steps and heart rate" (merged) | Not related — matched on an unrelated mention of a time value, not the lookback window. |
| `48h` | none | No hit. |
| `lookback` | none | No hit. |
| `history` | #44, #43, #52, #34, #6, #5, #15 | See below — closest is #6, still not the same feature. |
| `READ_HEALTH_DATA_HISTORY` | #44, #43, #6 | Same set as above. |

Of the `history` hits, the only one that touches the lookback/backfill space is **PR #6,
"Historical data sync"** (merged 2026-04-01, author @andrearosso). It added *manual* selection
of an arbitrary date range (including data older than 30 days, gated behind the
`READ_HEALTH_DATA_HISTORY` permission) for on-demand sync. That is a different mechanism solving
a different problem: PR #6 lets a user manually pull an old date range on request; this
submission makes the *scheduled/interval* sync self-heal after an outage, with no user action
required. The two are complementary, not overlapping, and PR #6's merge shows the maintainer is
receptive to expanding history/lookback handling, not opposed to it.

**PR #52, "fix: retry + throttle Health Connect reads to survive rate limiting"** (merged
2026-06-18) is the rate-limit backoff and inter-page throttling this submission's slicing is
built to route through — it is infrastructure the catch-up feature depends on, not a prior
attempt at the same feature.

None of #44, #43, #34, #5, #15 concern catch-up, backfill, or the lookback window; they matched
on unrelated uses of "history" (permission troubleshooting, storage/persistence requests, log
redaction).

**Verdict: no prior rejection found.** Nothing to answer or work around in the pull request body.

## Mock payload `measurement_location` mismatch

Searched for `measurement_location` and `mock payload`:

| Search term | Hits | Verdict |
|---|---|---|
| `measurement_location` | #25 "Add support for SkinTemperatureRecord" (closed, merged the feature) | Not the same bug. #25 is the original feature request that introduced `measurement_location` to the payload; it does not mention the mock endpoint or the string/int mismatch. It does confirm the field is documented as coming from Health Connect's integer `measurementLocation`, corroborating that the mock's string value is wrong. |
| `mock payload` | #65, #62 (both merged feature PRs) | Not related — matched on unrelated payload-field additions, no mention of the mock endpoint. |

**Verdict: no existing report found.** The mock payload bug fix in this batch does not duplicate
an open or closed issue/PR.

## Why the other two side commits are not going upstream

**`299e41e0` (dependency bump: Kotlin 2.1.21, Compose BOM 2025.09.01.00).** A version bump like
this belongs to the maintainer's own release cadence — they choose when to move the whole
project's toolchain forward, and doing it from a downstream PR risks colliding with work already
in flight upstream or breaking a pin they set deliberately. It also touches build configuration
that upstream's CI signs and publishes, which is not this fork's call to make. It fixes nothing a
user reported. It stays on the fork's `feat/update-dependencies` branch.

**`07030b59` (allow cleartext HTTP traffic in network security config).** This relaxes Android's
network security policy so the app can reach a LAN webhook target over plain HTTP without TLS.
That is a legitimate need for this deployment's specific LAN target, but it is a security
relaxation, and pushing a security relaxation into a public project on this deployment's behalf
is not something to do without the maintainer's explicit buy-in on the general case. If a
general, opt-in cleartext allowance is worth proposing upstream, it needs its own pull request
with its own justification — not a rider on the catch-up feature. It stays on the fork's
`feat/update-dependencies` branch.
