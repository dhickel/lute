# Actor behavior graphs and persistent modes

## Topic

Per-actor modes and behavior graphs for actor perception and movement.

## Source References

- `AwarenessSystem`, `BehaviorGraphs`, and `ActorAi` in `core/src/main/java/io/mindspice/lute/engine`.
- [Generic behavior graph traversal and Hoard areas](../specifications/decisions.md#generic-behavior-graph-traversal-and-hoard-areas), [Map actor awareness decision](../specifications/decisions.md#map-actor-awareness), and [Composite Characters](../specifications/composite-characters.md).

## Key Takeaways

Generic graph traversal takes an Ashley `Entity` first and a typed context second. It is distinct from the actor registry: NPCs and enemies retain `FocusState`, the `BehaviorGraphs` registry, and their per-actor Wander/Engage/Investigate FSM with simple explicit Java transitions. The current mode selects a behavior graph; the graph chooses actions within that mode. The graph package is `io.mindspice.lute.engine.behaviorgraph`, and actor Java accessors include `behaviorGraph()` and `behaviorGraphKey()`. Keep mode, timers, and last-seen coordinates per actor, not in shared graph state. This supersedes the earlier guidance that no actor FSM was needed.

Hoard enemy polygons resolve their existing `agroWanderingHoard` key through a separate Java-defined typed graph map. This is not general wiring for other `behaviorKey` tags, a data-defined graph loader, or a migration of other map keys. Spawn rules and Tiled format remain unchanged.

Enemy sight uses the required `detection_bounds.shape` authored in `enemies.json`: `FORWARD_SEMICIRCLE` filters the feet-centered circle to the inclusive forward half-plane, while `FULL_CIRCLE` does not. Both require feet-to-feet line of sight. Migration assigns `FULL_CIRCLE` to `dredge` and `FORWARD_SEMICIRCLE` to the other existing enemies, but spawn identity does not select the shape. NPC detection schema and behavior are unchanged. Only the Hoard Dredge guard normally returns directly to its spawn when detection is lost rather than investigating the last seen point. A damaging player arrow provokes pursuit beyond normal sight and range until solid body contact or the player is no longer live; normal detection and combat rules then resume, without pathfinding. Solid non-actor fixtures block sight, including at the ray origin; actors and sensors do not. Engage records the last seen world-space feet coordinates. On loss of detection, Investigate steers directly to that point and returns to Wander within 8 logical pixels or after 2 seconds, whichever comes first. Reacquisition is possible on any tick. Only live sight can request melee; the existing 32-pixel range, cooldown, and damage remain unchanged. Perception continues during attack, while motion and facing remain committed until the attack ends. The intended contract adds no pathfinding or saved data. NPCs do not acquire targets by default, but a living NPC or enemy damaged by a bow checks the living shooter's current position at impact. With feet-to-feet sight unobstructed by solid cover and within three times the victim's detection radius, it can identify and pursue the shooter; retaliation ignores facing, including for ordinary enemies. The Hoard guard's arrow-provoked pursuit is the exception to those range and sight checks. Confirmed nonlethal player melee damage also provokes ordinary enemies under the same range and cover constraints, without changing NPC melee behavior. Even an unarmed NPC can retaliate. Attacks still require live sight and the existing cooldown, and sight loss uses the existing Investigate behavior. Each NPC and enemy independently chooses each wander and pause duration from 1–3 seconds.

## Project Relevance

`BehaviorGraphs.BASE_MELEE` and `BehaviorGraphs.BASE_ARCHER` are suppliers that construct reusable engage subgraphs. Archer composition requires `BowAim`; the existing `wander_archer` factory supplies it. The default/base melee and archer decision delays are sampled once per eligible opportunity, at 25–100 ms and 150–300 ms respectively; these are pre-commit AI decision waits, not player attack timings. These decision waits are separate from animation windup, recovery, and cooldown. Lunge windup advances at twice the normal playback rate until its sensor is live; twirl uses fixed-step playback and its sensor starts on the first pose.

`AwarenessSystem` samples perception, updates the mode in `ActorAi`, and traverses the selected shared graph. Investigation arrival depends on the current feet position, not the steering offset from the last update. The circular range query itself remains unfiltered by facing: apply the semicircle check to enemy sight only when its authored shape requires it, not to retaliation, general role queries, or NPC retaliation. The physics debug overlay shows each enemy's authored detection shape without occlusion; NPC circles remain unchanged. For deterministic marker positioning and weapon/render comparisons, isolate AI and exercise it separately with live Box2D and combat updates. Validate retaliation at impact using the attacker's current position, living state, range, and solid cover; exercise nonlethal player melee and arrows from behind as well as sight loss, cooldown, committed windups, unarmed NPC arrow retaliation, and the absence of default NPC aggression. Check independently timed wander and pause intervals for multiple actors.

For live projectile tests, aim at the actual offset Box2D hurtbox rather than the body origin. Verify guard solid-feet contact separately from sensor contact; see `HoardArenaTest.verifyGuardArrowChase`.

## Open Questions

None for the accepted Wander/Engage/Investigate contract.
