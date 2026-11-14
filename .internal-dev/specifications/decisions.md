# Durable Decisions

## Cache-only input listeners

- **Decision:** `KeyListener` and `MouseListener` are concrete, independently owned input caches. Managers deliver to these caches, not immediate input listener callbacks. Controllers retain their own key queues. Mouse position updates the latest cached position; selected buttons and scroll enter its queue.
- **Justification and alternative:** Deferred queue consumption preserves the existing cache-based gameplay path without a separate immediate-delivery listener API. The former callback listener implementations and cache class names are removed rather than retained as compatibility aliases; this intentionally breaks the old API.
- **Caveats:** LibGDX event callbacks and `KeyActionConsumer`/`MouseEventConsumer` queue-drain consumers remain. Consumers run when callers consume or poll, not directly from managers. Queues are bounded, so overflow can lose transitions. `releaseHeldInputs()` queues balancing releases for enabled listeners selecting each captured action; callers must drain them outside event dispatch on the main thread.
- **Affected specifications:** This decision is the current input-listener contract; `docs/input-system.md` describes its application and lifecycle.
- **Source:** User-directed input listener simplification.
- **Review timing:** Revisit if the input delivery contract changes.

## Explicit combat attack lifetime

- **Decision:** GameData weapon `attack_duration` is a required, positive finite duration in seconds for a runtime weapon; the sword uses 0.5 seconds. Combat owns explicit active state and an elapsed timer. `AttackSpeed` affects animation playback only, not attack duration; animation follows combat state and does not end an attack. The attack sensor is a consequence of active combat, not the state itself. Existing `hitDelay` remains unchanged and unused.
- **Justification and tradeoff:** An explicit duration decouples gameplay timing from animation and fixture lifetime. Ordinary 60 Hz physics ticks are accepted without an extra contact refresh: an attack shorter than a tick can expire without a physics hit.
- **Affected specifications and sources:** This decision establishes the combat lifetime contract. Affected source paths are `assets/data/weapons.json`, `core/src/main/java/io/mindspice/lute/engine/data/json/WeaponsData.java`, `core/src/main/java/io/mindspice/lute/engine/components/Combat.java`, `core/src/main/java/io/mindspice/lute/engine/components/CombatWeapon.java`, `core/src/main/java/io/mindspice/lute/engine/systems/CombatSystem.java`, and `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`.
- **Source:** User request for explicit combat attack duration and acceptance of the physics-tick caveat.
- **Review timing:** Revisit if attack timing, animation ownership, or physics contact guarantees change.
