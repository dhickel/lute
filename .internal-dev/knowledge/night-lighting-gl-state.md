# Night lighting validation and ownership

## Topic
Collision-based night lighting, asset provisioning, and desktop GL state isolation.

## Source References
`RenderState`, `LwjglCompatibilityVertexArrays`, `NightLighting`, `LightingRenderTest`, `CallerVertexInput`, and `MapTravelTest`.

## Key Takeaways
- Shadows use active non-sensor Box2D fixtures, not visual silhouettes or collision filters, excluding the current emitter's entire body. Another filled fixture touching or containing the origin suppresses that light; chain loops do not acquire a filled interior. Per-light stencil masks must not leak between emitters. Query mounted live geometry after physics; do not retain retired bodies across frames or synchronous travel staging.
- A libGDX GL20 wrapper does not imply that the desktop driver lacks VAOs. Attribute pointers, their captured VBO bindings, enable bits, element binding and divisors belong to vertex input state; restoring only the current array-buffer binding or enable bit does not restore the caller's layout. Isolate renderer and compositor draws in their own VAOs when available. On pre-VAO compatibility GL, save and restore the client vertex-array stack, disable inherited generic attributes and reset supported divisors for the draw. A full client stack must fail explicitly. Exercise nondefault caller layouts and actual draws on both paths, including nested scopes and failure cleanup.
- A bound pixel-unpack buffer changes `glTexImage2D`'s pointer, including null, into a buffer offset. Unbind the caller PBO before allocating or uploading textures, neutralize row length and skips where supported, and restore both buffer binding and pixel-store state afterward. A deliberately undersized real PBO and nondefault unpack layout expose errors that default-state tests miss; cover lightmap resize, compositor targets and crosshair uploads.
- Split read/draw framebuffer bindings can exist on desktop GL20, including GL 2.1 with `GL_EXT_framebuffer_blit`, even when `Gdx.gl30` is null. Detect supported desktop capabilities rather than treating the wrapper as the capability test; restore read and draw targets separately. Test with distinct caller FBOs and the extension path, including exceptional allocation and draw paths. Avoid restoring deleted renderer-owned handles from nested state snapshots.
- A sampler object bound to texture unit zero overrides texture filtering and can make a level-zero-only lightmap or character texture incomplete. Temporarily unbind it for rendering and standalone composition, then restore it without changing other units or the caller's active unit. Test a mipmap-requiring sampler with actual pixels and interrupted draws.
- Non-indexed color-mask and blend setters broadcast to all draw-buffer slots. Preserve indexed masks and blend enables, plus independent blend functions and equations where supported, including unrouted slots. `glClear` affects every routed output: isolate the caller's output-zero destination throughout gameplay and restore the draw FBO's routes only after rendering. Test distinct routes and a secondary attachment sentinel in day, night and interrupted frames, not just state readback.
- Raw and generated game assets are ignored. Migrate the object catalogue and ambient settings in the source tree and regenerate; no missing-data fallback exists. Ambient is captured at startup, not hot reloaded. An isolated X11 regeneration and restart with brightness 0.35 then 0.65 changed average night RGB from (18, 22, 16) to (31, 38, 28); defaults and map hashes were restored.
- Synthetic parser and Box2D tests do not need ignored assets. Synthetic rendering still needs a real GL-capable display. Provisioned lifecycle and travel tests cover separate ownership paths. After repair `49c1db8`, the final lighting regression passed 4,212 combined checks on Mesa GL20 compatibility and GL30 core; the final `check` and four GL tasks passed in `/tmp/lute-jog.jg2c0W/final-after-mrt.log`, and final integration confirmation passed. Earlier probes exercised GL 2.1 extension and a no-native-VAO compatibility path before that repair. These runs do not establish coverage of every driver.

## Project Relevance
These boundaries prevent misleading visual or test conclusions when modifying the campfire, world rendering, asset migration, or map transitions.

## Open Questions
No new product direction is implied; additional drivers and installations require their own validation and asset migration.
