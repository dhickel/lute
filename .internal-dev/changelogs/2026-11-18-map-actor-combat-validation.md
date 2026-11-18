# Map Actor Combat Validation

## Date
2026-11-18

## Git Commit

64c1c6f34dbeacce2da2e889b8e7a549a8f709d7

## Change Summary
Exercise sword swings and fully drawn bow shots against the NPC and enemy spawned by the actual test map. Check exactly one authored damage application per hit and no damage on misses.

## Files
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/CompositeGameTest.java`
- `.internal-dev/knowledge/animation-initialization.md`

## Behavioral Impact
No runtime behavior changes. The desktop integration test now covers real map actor combat instead of relying solely on synthetic targets.

## Specification Impact
None; this verifies the existing map actor hurtbox contract without changing it.

## Risks
The test uses the current demo map and both configured weapons; it does not establish actor behavior at zero health.

## Follow-up Items
`./gradlew --offline check :lwjgl3:compositeRenderTest :lwjgl3:mapTravelTest` passed. The user separately completed the manual collision walkthrough.
