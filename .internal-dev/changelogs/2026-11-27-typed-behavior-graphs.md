# Typed behavior graphs and Hoard spawn markers

## Date

2026-11-27

## Git Commit

6e5199253e360aac4986bb1b51deb9dad3cc9c7f

## Change Summary

Made graph traversal generic over a typed context and used it to dispatch Hoard wave-area placement through the existing `agroWanderingHoard` key. Actor graphs retain `FocusState` and existing modes.

## Files

- `core/src/main/java/io/mindspice/lute/engine/behaviorgraph/` — typed traversal and actor-specific actions and decisions.
- `core/src/main/java/io/mindspice/lute/engine/systems/` — awareness and Hoard integration; added `HoardSpawnMarker`.
- `BehaviorGraphTest.java`, `MapSensorSystemTest.java`, and `HoardArenaTest.java` — traversal, placement, and preflight coverage.
- `core/build.gradle` — registered the graph test.
- `.internal-dev/specifications/decisions.md` and `.internal-dev/knowledge/actor-behavior-graphs.md` — documented the traversal and Hoard-area boundaries.

## Behavioral Impact

No gameplay, asset, or schema changes intended. Hoard placement continues through the existing placement implementation. Java graph callers use typed contexts and the extracted actor helpers.

## Specification Impact

Updated the durable decision to specify generic traversal and the separate Hoard graph map, with unchanged actor modes and spawn rules.

## Risks

Focused tests do not cover all random placement sequences, map configurations, or external Java callers. Missing or unsupported Hoard keys fail map preparation.

## Follow-up Items

Focused offline graph, awareness, combat, Hoard, map, arena, and Test Area tests passed, as did `git diff --check`. The full suite was not run.
