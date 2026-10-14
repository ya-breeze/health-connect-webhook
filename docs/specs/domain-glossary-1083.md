# health-connect-webhook domain glossary

Idea: https://ideaforge.ikoro.in/idea/1083

## Why

Agents need this project's current domain vocabulary. Domain reading rules exist but a root
glossary does not.

## How

Add a concise root CONTEXT.md using definitions verified against current source and repository
documentation. Preserve own instructions, code, configuration and data. Keep the glossary free
of implementation details, operational steps and environment tracker or triage configuration.
Existing contexts in other projects remain unchanged. This is documentation only.

## Validation Commands

./gradlew test
./gradlew lint
git diff --check

### Task 1: Record and merge the project vocabulary
- [x] Define the current domain terms and important distinctions without inventing future behavior.
- [x] Verify every definition against source and the existing domain-doc pointer.
- [x] Run applicable native checks and record relevant E2E limitations.
- [x] Run the Review Gate and resolve verified findings.
- [x] Prepare the reviewed result for the owner-authorized merge.
- [x] Mark completed.
