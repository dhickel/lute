# Add Hoard asset preflight and address PR #49 review follow-ups

## Date
2026-11-26

## Git Commit

d1d2bd0ed43191ed06a9c182d38bc7114e7a9e90

## Change Summary
Add default-on, configurable Hoard asset preflight, correct arena provisioning instructions, and address the six nonblocking PR #49 findings.

## Files
Game settings, Options, arena preparation, encounter and combat systems, focused tests, shared geometry fixtures, and Hoard documentation.

## Behavioral Impact
Incomplete delayed Hoard dependencies fail before arena installation when validation is enabled. Authored roster enemies receive kill credit and health-drop rolls. Explicit visual profiles override saved visual options without changing asset validation. Fast crossing targets can be intercepted. Encounter tuning is unchanged.

## Specification Impact
Documents the required Asset Validation field, Options behavior, and current Hoard provisioning and gameplay rules.

## Risks
Ignored local settings must be migrated externally. The three existing catalog, composite, and lighting test failures reproduced on the validation baseline.

## Follow-up Items
Resolve kill XP attribution at round rollover, fix the remaining test fixtures, and identify a reproducible external asset pack.
