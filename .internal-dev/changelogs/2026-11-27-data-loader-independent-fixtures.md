## Date
2026-11-27

## Git Commit

b538c6b3c3ce88c65abace9a2c58c2e30cfecb40

## Change Summary
Replace live asset assertions in `DataLoaderTest` with synthetic classpath fixtures. Add a repository test rule against pinning live tunables or asserting values that do not exercise behavior.

## Files
`AGENTS.md`, `DataLoaderTest.java`, and `core/src/test/resources/data-loader/`.

## Behavioral Impact
None in production. The loader tests exercise parsing, malformed and missing data, references, and behavior that distinguishes the two attack slots.

## Specification Impact
None; loader contracts are unchanged.

## Risks
The focused test no longer verifies live pack contents. Pack validation belongs in separate integration checks.

## Follow-up Items
`:core:dataLoaderTest` passed. Senior validation also ran from an empty working directory and verified that mutations sorting the weapon cycle or reusing the primary attack fail. GitHub issue #50 remains open while the fix is on the development branch.
