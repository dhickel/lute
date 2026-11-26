# HTML5 export proof of concept

## Topic
Resuming the deferred Hoard web alpha from the validated, separate browser experiment.

## Source References
- `hoard-mode-alpha-web` at `e1d445b`: `README.md`, `web/build.gradle`, and `web/test/gameplay.cjs`.
- On that branch, see `.internal-dev/reviews/2026-11-25-web-release-performance-review.md` and `.internal-dev/reviews/2026-11-25-web-release-diagnostics-validation-review.md`.

## Key Takeaways
The proof of concept compiles shared Java 25 game code to a static browser export with TeaVM 0.17.0, gdx-teavm 1.6.2, and libGDX 1.14.2. Web release builds disable GL profiling, debug overlays, FPS logging, tab-title draw counts, debug logs, and compiler debug metadata without changing desktop diagnostics.

In idle Hoard on Chromium 149 with ANGLE/Radeon at 960×540 and DPR 1, the baseline ran at 34.1–35.7 FPS. Removing title updates and FPS output alone did not improve it. Disabling GL interception reached 60 FPS even with overlays still on; the baseline made 440–452 WebGL `getError()` calls per frame, versus zero in release. Three short runs per variant under a 60 Hz ceiling do not predict combat, night lighting, larger canvases, or other devices. Profile the affected device and scene before changing the renderer.

## Project Relevance
The experiment stays separate from `hoard-mode-alpha`; it is not merged or deployed. When ready for web alpha, review `hoard-mode-alpha-web` at `e1d445b`, reconcile it with current desktop Hoard changes, then rebuild and revalidate.

On the web branch, provision the complete ignored `raw_assets/` tree first. The export stages generated assets and does not generate them itself:

```sh
./generate-runtime-assets.sh
./gradlew :web:clean :web:dist
python3 -m http.server 8000 --bind 127.0.0.1 --directory build/web/dist/webapp
```

Open `http://localhost:8000/`. The prototype requires JavaScript and WebGL with `EXT_blend_minmax`; browser audio may require clicking the canvas. For the automated smoke test, use Node with `playwright-core` and run `CHROME=/path/to/chrome node web/test/gameplay.cjs http://localhost:8000/`. Previous Chromium checks covered Hoard startup, waves, keyboard input, weapon audio, resizing, simulated controller input, and fixture-based map travel, destination music, and night rendering. The smoke test used software WebGL and a response-only travel fixture, not physical controller or cross-browser testing.

## Open Questions
- Which browsers, devices, controllers, and static hosting target should the alpha support?
- Do the current assets, gameplay, and target-device performance satisfy the eventual release criteria?
