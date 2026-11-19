# Independent attack cooldowns

## Date
2026-11-18

## Git Commit

9161b3c36dd114f075036245422e3c751f63e8e8

## Change Summary
PR #30 originally used a fighter-wide timer. Give each weapon's primary and secondary attack its own cooldown on each fighter. Definitions and tuning in `weapons.json` are unchanged.

## Files
- `Combat.java`, `ControllerSystem.java`, and `CombatSystem.java` for attack-keyed timers and input gating.
- `CombatSystemTest.java` and `MapTravelTest.java` for slot, weapon switch, and reset coverage.

## Behavioral Impact
A ready attack can cancel the previous animation even if the previous attack is still cooling down. Inactive weapon timers continue to age and switching back does not reset them.

## Specification Impact
Updated the composite-character and timing decisions to specify per-weapon, per-slot cooldowns instead of one fighter-wide timer.

## Risks
A different attack can interrupt a wind-up before its hit or sound cue. Contacts already queued by physics are processed before interruption.

## Follow-up Items
Manual gameplay tuning remains for PR review. Gradle `check` and desktop composite rendering, screen lifecycle, and map travel suites passed.
