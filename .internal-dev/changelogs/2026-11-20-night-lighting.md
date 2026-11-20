# Night lighting and collision shadows

## Date
2026-11-20

## Git Commit

f689a5aa5aa7244e4c2084a34132880613c0bdca

## Change Summary
Added a session day/night toggle, required authored ambient and emissive data, pulsing campfire illumination, and independent collision shadows. Added parser, physics, lifecycle, travel, and real-GL regressions.

## Files
Core data, input, screen, factory and rendering systems; `assets/input.setting.json`; core and LWJGL3 tests and build tasks; `README.md`, `docs/asset-service.md`, `docs/input-system.md`, and living decisions and horizon ideas. Local ignored raw and generated settings and object catalogue were migrated; game assets remain untracked.

## Behavioral Impact
N toggles night on fresh active presses. Each screen starts in day and retains mode and pulse phase across travel and hide/show; pause and hide freeze phase. Night modulates world art with ambient and occluded lights; overlays remain unlit. World time is independent. Asset provisioning requires explicit nullable object fields and required `nightAmbient`; regenerate and restart to apply ambient changes.

## Specification Impact
Updated durable decisions for lighting, input and authoring, and corrected the horizon description of automatic day/night cycles.

## Validation
The full `./gradlew check` and focused checks passed. Synthetic parsing and native Box2D tests passed; real-GL lighting, compositor, lifecycle and travel regressions passed, including desktop compatibility, core and GL 2.1 extension paths. Live smoke compared day, night, pulse, movement/shadow and return-to-day screens. An isolated X11 restart test changed authored ambient brightness from 0.35 to 0.65, regenerated assets and restarted: average night RGB changed from (18, 22, 16) to (31, 38, 28). Defaults and map hashes were restored afterward. A separate user-approved temporary foreground scene passed live day/night smoke: 10,986 differing foreground pixels dimmed, and 54 pure-white crosshair pixels remained unchanged across day and night with aim held. Evidence is local in `/tmp/lute-jog.jg2c0W/foreground/results.log` and `smoke.log`; raw and generated maps were restored byte-for-byte and verified with SHA-256.

## Risks
Shadows follow Box2D fixtures, not art silhouettes. GL tests require a capable display; game and provisioned integration tests require external assets.

## Follow-up Items
Other installations must migrate their ignored raw assets and regenerate the runtime tree. Smoke images are local only; no remote URLs are available.
