# Actor last-seen investigation

## Date

2026-11-21

## Git Commit

7164863ba29ab6931ee4670e2d2d5bbc3b49e377

## Change Summary

Added persistent Wander, Engage, and Investigate modes that select the decision graph for each update. The attacker remembers the player's last visible feet position instead of immediately wandering after losing sight.

## Files

- `ActorAi.java`, `AwarenessSystem.java`, and `Graphs.java`: mode transitions, perception, and mode-specific graph selection.
- `AwarenessSystemTest.java`: regression coverage for occlusion, reacquisition, attack commitment, arrival, and timeout.
- README, actor specifications, and actor decision graph knowledge note.

## Behavioral Impact

A wandering attacker investigates the remembered point until reacquisition, arrival within eight logical pixels, or a two-second timeout. It cannot initiate melee from memory. Perception continues during an active swing without overriding movement or facing. NPC wandering, combat damage, asset data, and save formats are unchanged.

## Specification Impact

Supersedes the stateless-pursuit behavior with an explicit actor state machine and transient last-seen memory.

## Risks

Investigation steers directly to the remembered point and does not route around obstacles; the timeout bounds a blocked investigation.

## Follow-up Items

Focused data loader, awareness, combat, map travel, and composite rendering checks passed. Manual corner pursuit was not tested.
