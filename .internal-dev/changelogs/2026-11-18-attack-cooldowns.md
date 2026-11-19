# Attack cooldowns and recovery cancellation

## Date
2026-11-18

## Git Commit

32983a6c8535b3940aba8647e5c6cd2d77ac261b

## Change Summary
Separate attack cadence from animation completion. Melee acceptance and arrow release start a shared fighter cooldown; fresh input after expiry replaces the current attack without buffering earlier inputs.

## Files
- Weapon data, runtime combat, animation playback, and gameplay system registration.
- Core combat and JSON checks, desktop rendering and map travel fixtures, README, and combat specifications.

## Behavioral Impact
Replacing recovery removes the old melee sensor after pending hits are resolved, starts the new animation from its first frame, and leaves in-flight arrows alone. Bow draw and aim cancellation do not start the cooldown. The authored local weapon data and its generated counterpart were migrated; these assets are not tracked.

## Specification Impact
Updated `composite-characters.md` and `decisions.md` with the data contract, timing, and recovery rules.

## Risks
A cooldown shorter than the wind-up can let a later attack replace playback before its hit or sound cue. Older external weapon data requires migration.

## Follow-up Items
Manual tuning and playtesting. Offline `check` and desktop composite render, screen lifecycle, and map travel suites passed in an isolated copy with generated assets.
