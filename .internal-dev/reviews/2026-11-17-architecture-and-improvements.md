# Architecture and existing-behavior simplification review

## Scope
Broad architecture/readability review across all production packages in core, lwjgl3 and util at baseline `33dbd077ff1c66bf211971086b77caf2c8800563`, supported by independent correctness and documentation reviewers. Recommendations concern existing behavior only; no new features, implementation, CI, dependency pinning, generalized frameworks or speculative API extensions are included. The architecture lens inventoried every package and traced key flows, not every leaf implementation line.

## Findings
### Current architecture
- **core** owns the LibGDX/Ashley runtime: GameCore composition, shared input/assets/engine, screens, JSON boundaries, components, events, animation, physics/combat, rendering and Tiled maps/triggers.
- **lwjgl3** owns desktop startup/platform support and the actual-asset/synthetic GL regression harnesses.
- **util** owns transactional raw-to-runtime asset generation and ownership/rollback validation.
- Runtime flow: input maps device actions into per-controller bounded queues; ControllerSystem updates movement and attack requests; animation drives combat timing; unlocked physics processing applies contact effects; the compositor builds complete layered characters, then rendering world-depth-sorts them with ordinary sprites.
- TiledSystem owns map entities and static bodies; EntityFactory constructs validated entities. AssetService owns typed resources and distinguishes borrowed atlas textures from map-owned terrain textures.
- Existing specifications are broadly aligned. Keep explicit EventListener/EventEmitter participation, validation at external-data/asset boundaries, and Loadout/Combat ownership separation.

### Prioritized optional refactors
| Priority | Target | Smallest useful change | Benefit | Risk / validation |
| --- | --- | --- | --- | --- |
| Low-risk first | `core/src/main/java/io/mindspice/lute/engine/GameCore.java:119–141`: render/input/logic/draw | Remove empty input() and logic() calls/methods; forward rendering directly rather than through a one-line draw wrapper. | Makes the actual frame path obvious; removes no-op stages rather than inventing future hooks. | Low. Confirm no external/subclass callers before narrowing/removing methods; compile and exercise loading/game rendering and lifecycle forwarding. |
| Next, only with geometry coverage | `core/src/main/java/io/mindspice/lute/engine/systems/TiledSystem.java:156–215`: initMapBoundary | Use one small private helper for a boundary box fixture, called four times with the existing half-extents/centers. | Puts the four wall dimensions side by side; removes repeated shape creation/disposal/friction setup. | Moderate physics regression risk. Preserve exact coordinates, thickness, friction, ownership and disposal; add direct wall-geometry/body-count assertions. No general fixture builder. |
| Low value / optional API cleanup | `core/src/main/java/io/mindspice/lute/engine/systems/RenderSystem.java:154–165`: cleanMapLayers/loadMapLayers | Consider narrowing these helpers only after accounting for direct test and external callers. Production uses setMap; CompositeRenderTest deliberately calls the helpers. | Smaller public surface and clearer ownership. | Not a drop-in visibility change: test seam and compatibility must be preserved. Existing layer-order and real-GL tests must pass. Do not pursue solely for aesthetic purity. |

### Correctness-driven cleanup, not optional refactoring
- GameScreen installs systems on a shared engine but does not detach them before disposing its World. Make screen-owned lifetime explicit through direct ownership/removal; avoid a new service hierarchy. [Issue #16](https://github.com/dhickel/lute/issues/16).
- AudioSystem subscriptions need matching lifetime cleanup through the existing event interfaces. [Issue #8](https://github.com/dhickel/lute/issues/8).
- ScreenManager duplicate keys can silently displace screens. No duplicate-key caller was found. A small explicit reject-or-replace policy plus focused tests is preferable to redesigning screen management. Choosing that policy is follow-up work, not an accepted contract in this audit.

### Preserve justified complexity
No evidence-backed simplification justified weakening CharacterCompositor texture-page batching, directional layer order, isolated cells or premultiplied alpha; these solve observable overlap/edge problems. Likewise retain deferred Box2D contact work, attack/frame timing, hurtbox separation, multi-fixture logical sensors, concave geometry processing and boundary validation. Removing these mechanisms is not a readability improvement if it breaks existing functionality.

No justified rewrite of typed asset storage or deferred per-listener input queues was found. No abstraction-heavy split of EntityFactory, universal validation framework, ECS replacement, new feature hook or compatibility alias is recommended.

## Risk Assessment
The strongest opportunities are small and local. Lifecycle fixes have higher functional value than broad class splitting. Boundary helper extraction is worthwhile only if geometry assertions make the behavior-preserving claim testable. Public helper visibility changes have comparatively low value and should not break deliberate tests or external use.

## Recommendations
1. Prioritize the confirmed lifecycle/resource bugs over cosmetic refactors; see the separate [bugs and issues report](2026-11-17-bugs-and-issues.md).
2. If readability work is authorized, start with GameCore's no-op frame stages as a focused unit.
3. Extract boundary fixture repetition only together with direct geometry coverage.
4. Treat render-helper visibility as optional, not required cleanup.
5. Keep current data-driven design, event participation interfaces and source-boundary validation conventions.

## Follow-ups
This audit changes documentation and records only. No refactors were implemented or accepted as new specifications. Current isolated generation, registered core/util checks, and explicit actual-asset/synthetic GL validation are summarized in the bugs report; the unfiltered check retains known issue #6. Any later refactor needs its own branch, focused behavior tests, validation and merge. Live visual bounds acceptance remains separate from automated framebuffer success.
