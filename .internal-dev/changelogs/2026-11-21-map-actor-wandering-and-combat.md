# Map actor wandering and combat

## Date

2026-11-21

## Git Commit

e6bee5ae6ab38a5df2e4eae341cc9a476c189a53

## Change Summary

Consolidated role-specific definitions into `actors.json`, resolved by spawn type and key. Each actor specifies a required Java `decision_graph`. Added reusable decisions and actions, and ran the selected graph for each map actor before movement and animation.

## Files

- Core actor data loader and records, decision graphs and `ActorAi`, spawn factory, `AwarenessSystem`, and `GameScreen`.
- Core and desktop tests, the tracked actor fixture, and the local raw and runtime actor catalogues.
- `README.md`, `docs/asset-service.md`, composite character and decisions specs, and the [state-machine research note](../knowledge/actor-behavior-graphs.md).

## Behavioral Impact

Dynamic NPC and enemy bodies use authored move speed to wander. The `wander_attack` graph stops and faces a living player within 32 logical pixels and clear sight to request a primary melee attack. Existing combat handles cooldown, animation, and damage. Actors initially idle briefly to show the authored spawn facing. There is no chase, pathfinding, or new actor FSM.

## Specification Impact

Updated the actor authoring and runtime behavior contracts. Recorded persistent modes as a horizon idea.

## Risks

Local wandering changes direction periodically but does not route around obstacles or enforce a roaming boundary. Deterministic rendering and damage fixtures disable AI where exact poses and placement matter.

## Follow-up Items

Revisit navigation and persistent modes if gameplay starts to require pursuit or recovery around obstacles. Validation passed: actor data loader, awareness, native combat, map triggers, map sensors, desktop map travel (including wandering and an enemy hit), composite rendering, and desktop screen lifecycle.
