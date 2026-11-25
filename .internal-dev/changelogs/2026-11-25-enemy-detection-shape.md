# Author enemy detection shapes

## Date
2026-11-25

## Git Commit

ea8a8a9107bf8899bbbebbb6122d0481849cf8b2

## Change Summary
Require `detection_bounds.shape` in enemy data and use it for both perception and the physics debug overlay. Publish the migrated local enemy data.

## Files
`MapActorData.java`, `EntityFactory.java`, data-schema and map-travel tests, local raw and generated `enemies.json`, and the relevant README, awareness specifications, and knowledge records.

## Behavioral Impact
Existing radii and sight behavior are preserved: Dredge uses `FULL_CIRCLE`; the other enemies use `FORWARD_SEMICIRCLE`. NPC schema, circular range queries, line of sight, retaliation, and guard pursuit do not change.

## Specification Impact
Enemy sight shape is authored alongside radius and is no longer inferred from the spawn key.

## Risks
Other checkouts must migrate their ignored enemy data and regenerate runtime assets before starting the game.

## Follow-up Items
Track upstream integration in [issue #48](https://github.com/dhickel/lute/issues/48). Schema, awareness, input, combat, live Hoard, and map-travel checks passed; the existing broader failures were reproduced on the baseline.
