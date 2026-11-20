# Lighting debug views

## Date
2026-11-20

## Git Commit

edcd2ca1cae118f93857936be448e9fa30749e68

## Change Summary
Add an F3 cycle for Off, Raw Map, and Collision lighting diagnostics, independently of the N day/night toggle.

## Files
- `assets/input.setting.json`, `InputAction.java`, `GameScreen.java`: binding, action, session selection and input lifecycle.
- `Transform.java`, `RenderSystem.java`, `NightLighting.java`, `NightLightMesh.java`, `FixtureOutlines.java`, `LightingOutlines.java`, and `TriggerDebugRenderSystem.java`: lightmap and collision diagnostics, shared fixture tracing and centered emitters.
- `InputSettingsTest.java`, `GameScreenLifecycleTest.java`, and `LightingRenderTest.java`: binding, lifecycle and real-GL coverage.
- `docs/input-system.md`, `.internal-dev/specifications/decisions.md`: input and decision contracts.

## Behavioral Impact
Raw Map replaces world art with the combined lightmap at night and leaves day unchanged. Collision draws active non-sensor fixture outlines and current pulsed emitter rings in day or night, tessellating the visible ring arc at viewport scale. Selection persists across travel and hide/show but starts Off in a new session; stale and held inputs do not cycle across context changes.

## Specification Impact
The lighting debug decision and input documentation record the mode order, rendering behavior and session ownership.

## Risks
Collision outlines reflect physics rather than art. Automated Mesa GL tests do not establish appearance or behavior on every driver; no new live visual test is claimed.

## Follow-up Items
Full check and four GL suites passed, including 7,024 lighting checks and 223 lifecycle checks.
