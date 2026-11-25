# Attack cooldown identity

## Topic
Attack data, cooldown identity, and animation cancellation.

## Source References
- [Composite character specification](../specifications/composite-characters.md)
- [PR #30](https://github.com/dhickel/lute/pull/30)

## Key Takeaways
A duration authored per attack does not make timers independent. The player can alternate a weapon's primary and secondary attacks, or switch weapons when allowed; neither action clears that player's running timers. Track cooldown by stable weapon definition ID plus attack slot, not by animation name or attack object identity. Different attacks can share a pose (such as `jab`), and a weapon can use the same attack record for both slots.

Author the hit frame from the visual hit cue using the animation's 0-based frame index and its attack interval and speed. When tuning a cooldown to permit a same-slot repeat after the hit, budget for actual physics ticks and animation-frame granularity, not just the nominal time to that frame. Even while stationary, a ready attack in the other slot can preempt the current attack before its hit under the existing cancellation rules.

## Project Relevance
When adding an attack input, test a rejected repeat as well as a ready attack in the other slot and a weapon switch away and back.

## Open Questions
Manual playtesting will determine whether the authored timings need tuning.
