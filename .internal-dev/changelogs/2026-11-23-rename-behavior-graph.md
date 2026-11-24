# Rename decision graphs to behavior graphs

## Date

2026-11-23

## Git Commit

dd9c7d5e08367ddb9a67d7190426c58ba382afba

## Change Summary

Renamed the engine package and registry to use behavior-graph terminology, which is not limited to character AI.

## Files

Behavior graph package and registry, actor data and call sites, data loader and map travel tests, README, actor specifications, and the behavior-graph knowledge note.

## Behavioral Impact

Graph traversal, actor behavior, and existing graph profile values are unchanged. Actor JSON now requires `behavior_graph`; `decision_graph` has no alias. Both ignored local actor asset trees were migrated. Data loading, native awareness, and map travel tests passed.

## Specification Impact

Updated the actor authoring and awareness contracts to reflect the renamed JSON field and graph terminology.

## Risks

Other checkouts with existing ignored assets need to rename the field in `raw_assets/data/characters.json` and `raw_assets/data/enemies.json`, then regenerate runtime assets.

## Follow-up Items

None beyond the asset migration in other checkouts.
