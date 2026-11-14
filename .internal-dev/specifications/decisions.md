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

## Custom asset service and logical keys

- **Decision:** Replace the AssetManager-backed service with a directly owning service whose logical identity is resource class and logical ID. Store entries in four typed maps selected directly by class, with a separate ordered loading `queue`. The maps alone track registered assets; diagnostics and disposal iterate them without a redundant ownership index or a cross-type insertion-order guarantee. Asset records define loads; runtime audio events and animation components carry IDs. Manifest keys remain IDs, and the manifest name is the map ID. The service supports sounds, music, atlases, and tiled maps.
- **Justification and tradeoff:** Explicit keys separate runtime identity from file paths and make ownership and unloading direct. The standalone comparison favored typed lookup in the measured workloads and showed a separate benefit from avoiding scans of loaded entries during queue updates; it does not establish a universal speedup or gameplay improvement. The complete composite implementation remains test-only for comparison. Unlike AssetManager, the service has no background loading or reference counting: queue updates synchronously load at most one entry, while an explicit load handles only its requested entry. Repeating the same key and path is idempotent; conflicting paths fail while the key exists.
- **Caveats:** Load on the render thread. Failed loads remove their entries for explicit retry; a `LoadingScreen` update failure is propagated and latched until `show()` resets the screen and queues its definitions again. Unloading cancels queued work or disposes the loaded resource; users must stop using it and its dependents first. Different keys for the same path own independent loads. Direct atlas and map loaders retain their textures until ownership transfers to the completed resource and dispose already-created textures on runtime failure; `DirectTexture` disposes its GL handle if loading fails during construction. Service disposal is idempotent.
- **Affected specifications:** This decision establishes the asset identity, loading, and ownership contract; `docs/asset-service.md` describes usage and migration.
- **Source:** User-directed replacement of AssetManager-backed asset handling; `.internal-dev/reviews/asset-storage-benchmark-comparison.md`.
- **Review timing:** Revisit if asset identity, loading, or ownership contracts change, or relevant measurement evidence changes.
