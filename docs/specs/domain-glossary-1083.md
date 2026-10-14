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

git diff --check

### Task 1: Record and merge the project vocabulary
- [ ] Define the current domain terms and important distinctions without inventing future behavior.
- [ ] Verify every definition against source and the existing domain-doc pointer.
- [ ] Run applicable native checks and record relevant E2E limitations.
- [ ] Run the Review Gate and resolve verified findings.
- [ ] Verify the exact merge and canonical Completion outcome.
- [ ] Mark completed.
