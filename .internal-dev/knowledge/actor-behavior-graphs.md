# Actor behavior graphs and persistent modes

## Topic

Per-actor modes and behavior graphs for actor perception and movement.

## Source References

- `AwarenessSystem`, `BehaviorGraphs`, and `ActorAi` in `core/src/main/java/io/mindspice/lute/engine`.
- [Map actor awareness decision](../specifications/decisions.md#map-actor-awareness) and [Composite Characters](../specifications/composite-characters.md).

## Key Takeaways

The accepted actor design uses a per-actor Wander/Engage/Investigate FSM with simple explicit Java transitions. The current mode selects a behavior graph; the graph chooses actions within that mode. The name covers actions as well as decisions; it does not imply generic non-actor traversal. The graph package is `io.mindspice.lute.engine.behaviorgraph`, with registry `BehaviorGraphs` and Java accessors `behaviorGraph()` and `behaviorGraphKey()`. Keep mode, timers, and last-seen coordinates per actor, not in shared graph state. This supersedes the earlier guidance that no actor FSM was needed.

The existing feet-centered sensor and feet-to-feet line of sight acquire a living player. Engage records the last seen world-space feet coordinates. On loss of detection, Investigate steers directly to that point and returns to Wander within 8 logical pixels or after 2 seconds, whichever comes first. Reacquisition is possible on any tick. Only live sight can request melee; the existing 32-pixel range, cooldown, and damage remain unchanged. Perception continues during attack, while motion and facing remain committed until the attack ends. The intended contract adds no pathfinding or saved data. NPCs do not acquire targets by default, but a living NPC or enemy damaged by a bow checks the living shooter's current position at impact. With feet-to-feet sight unobstructed by solid cover and within three times the victim's detection radius, it can identify and pursue the shooter; even an unarmed NPC can retaliate. Attacks still require live sight and the existing cooldown, and sight loss uses the existing Investigate behavior. Each NPC and enemy independently chooses each wander and pause duration from 1–3 seconds.

## Project Relevance

`AwarenessSystem` samples perception, updates the mode in `ActorAi`, and traverses the selected shared graph. Investigation arrival depends on the current feet position, not the steering offset from the last update. For deterministic marker positioning and weapon/render comparisons, isolate AI and exercise it separately with live Box2D and combat updates. Validate retaliation at impact using the shooter's current position, living state, range, and solid cover; also exercise sight loss, cooldown, unarmed retaliation, and the absence of default NPC aggression. Check independently timed wander and pause intervals for multiple actors.

## Open Questions

None for the accepted Wander/Engage/Investigate contract.
