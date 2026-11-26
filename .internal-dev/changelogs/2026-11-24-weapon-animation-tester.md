# Standalone weapon animation tester

## Date
2026-11-24

## Git Commit

81f4ad71d7a25f5892c312dfdc541e5778e6e47d

## Change Summary
Add a separate JavaFX editor for previewing and tuning weapon attack bounds against the composed player sprite and its hurtbox and physical collider.

## Files
- `weapon-tester/` and `settings.gradle`
- `README.md` and `.internal-dev/specifications/composite-characters.md`

## Behavioral Impact
Launch with `./gradlew :weapon-tester:run`. The editor reads raw data and sprites and saves only the selected attack's changed bounds values. Projectile-primary weapons expose only the secondary attack. Gameplay and runtime asset generation are unchanged.

## Specification Impact
The composite character specification records the standalone authoring workflow.

## Risks
Validated on Linux with eight unit tests and two display smoke tests. The stale-file check does not make simultaneous saves from separate editors safe. Legacy loader and GL checks assume the stock weapon catalog; they passed with stock assets but not with the expanded local catalog.

## Follow-up Items
Windows and macOS desktop launch were not exercised.
