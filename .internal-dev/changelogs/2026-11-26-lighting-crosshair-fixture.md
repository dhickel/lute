# Fix lighting crosshair test fixture

## Date
2026-11-26

## Git Commit

f6c5058e8a704bb67eff67d87d92fd777243250d

## Change Summary
Give the lighting integration test’s bow aimer a `Controller` so it qualifies for player crosshair rendering.

## Files
`lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/LightingRenderTest.java`

## Behavioral Impact
None in production. The test now reaches the crosshair upload with the caller’s pixel unpack buffer bound, keeping the existing pixel expectations.

## Specification Impact
None. The fixture now follows the existing player-only crosshair behavior.

## Risks
No production code changed.

## Follow-up Items
None. All 10,377 lighting assertions passed across GL20 and GL30 contexts.
