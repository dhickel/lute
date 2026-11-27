## Date
2026-11-27

## Git Commit

d7f234f9fb8028225268a84d676572eaffbe423a

## Change Summary
Make gameplay startup transactional and present a black loading frame before synchronous asset processing.

## Files
`GameCore`, `AssetService`, `TiledSystem`, startup screens, desktop regressions, and startup ownership guidance.

## Behavioral Impact
Recoverable startup failures return to the menu and permit retry. Direct-map startup preserves the original error. Rollback preserves pre-existing asset registrations and disposes attempt-owned resources.

## Specification Impact
Documents startup ownership and defers cycling loading backgrounds.

## Risks
Asset loading and map mounting remain synchronous. Validation used temporary master-compatible copies of the local assets.

## Follow-up Items
Item 2 of #52, cleanup ownership for the staged player body, is separate. Six focused tasks passed, including 463 real-GL startup checks. Composite rendering still fails a sword secondary contact assertion also observed on unchanged master.
