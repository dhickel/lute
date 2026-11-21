# Actor decision graphs and persistent modes

## Topic

Integrating persistent actor modes with the Java Decision Graph and gdx-ai state machines.

## Source References

- `AnimationPlayer`, `AnimationState`, `AwarenessSystem`, `Graphs`, and `ActorAi` in `core/src/main/java/io/mindspice/lute/engine`.
- [gdx-ai DefaultStateMachine implementation](https://github.com/libgdx/gdx-ai/blob/master/gdx-ai/src/com/badlogic/gdx/ai/fsm/DefaultStateMachine.java) and [state machine guide](https://github.com/libgdx/gdx-ai/wiki/State-Machine).

## Key Takeaways

`AnimationPlayer` owns a `DefaultStateMachine<Entity, AnimationState>`; the enum states are shared and operate on the owning entity. gdx-ai sets the initial state without calling `enter`. An update invokes global logic first and then the current state. `changeState` calls exit and enter even if the new state is the same, so guard against duplicate transitions.

- **Graph selects a persistent mode.** Evaluate graph conditions at defined decision points and transition only when the selected mode changes. The mode owns its lifecycle until a meaningful transition; do not reassert it every tick.
- **FSM selects a graph for its current mode.** The state's update traverses its graph for immediate actions. The FSM owns explicit mode transitions while the graph makes choices within a mode. Keep timers and other per-actor data on the entity, not shared enum states.

Wandering only needs a per-actor timer. Combat already owns attack timing, cooldown, and damage, and the existing animation FSM reacts to movement and combat. A second machine is not needed for these behaviors.

## Project Relevance

`AwarenessSystem` traverses shared graphs for actors with `ActorAi`, which owns the wander timer. The initial 0.25-second idle preserves the visible authored spawn facing. For deterministic marker positioning and weapon/render comparisons, isolate AI and exercise it separately with live Box2D and combat updates.

## Open Questions

Would future behaviors such as pursuing or returning home need to persist after the triggering condition disappears? If so, decide which layer owns transitions and add one machine only if its lifecycle callbacks simplify the implementation.
