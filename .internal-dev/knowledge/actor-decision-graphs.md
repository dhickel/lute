# Actor decision graphs and persistent modes

## Topic

Per-actor modes and decision graphs for actor perception and movement.

## Source References

- `AwarenessSystem`, `Graphs`, and `ActorAi` in `core/src/main/java/io/mindspice/lute/engine`.
- [Map actor awareness decision](../specifications/decisions.md#map-actor-awareness) and [Composite Characters](../specifications/composite-characters.md).

## Key Takeaways

The accepted actor design uses a per-actor Wander/Engage/Investigate FSM with simple explicit Java transitions. The current mode selects a decision graph; the graph chooses actions within that mode. Keep mode, timers, and last-seen coordinates per actor, not in shared graph state. This supersedes the earlier guidance that no actor FSM was needed.

The existing feet-centered sensor and feet-to-feet line of sight acquire a living player. Engage records the last seen world-space feet coordinates. On loss of detection, Investigate steers directly to that point and returns to Wander within 8 logical pixels or after 2 seconds, whichever comes first. Reacquisition is possible on any tick. Only live sight can request melee; the existing 32-pixel range, cooldown, and damage remain unchanged. Perception continues during attack, while motion and facing remain committed until the attack ends. No pathfinding, saved data, target references, or new JSON are added. The NPC only wanders.

## Project Relevance

`AwarenessSystem` samples perception, updates the mode in `ActorAi`, and traverses the selected shared graph. Investigation arrival depends on the current feet position, not the steering offset from the last update. For deterministic marker positioning and weapon/render comparisons, isolate AI and exercise it separately with live Box2D and combat updates.

## Open Questions

None for the accepted Wander/Engage/Investigate contract.
