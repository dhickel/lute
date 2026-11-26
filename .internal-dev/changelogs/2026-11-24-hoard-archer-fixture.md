# Hoard archer fixture

## Date
2026-11-24

## Git Commit

ceb3c2aa78722148db6188f5225533e1c5335496

## Change Summary
Integrate the archer PR and document the ignored hoard arena archer fixture and regeneration steps.

## Files
Hoard integration code, desktop arena tests, `docs/asset-service.md`, and the composite-character specification.

## Behavioral Impact
The authored archer spawns once per arena mount and counts toward the current run's score. Existing melee waves are unchanged.

## Specification Impact
The Hoard branch authoring contract uses `behavior_graph`.

## Risks
The desktop arena check requires the documented local asset fixture. Combat, awareness, input and arena regression tests passed.

## Follow-up Items
None.
