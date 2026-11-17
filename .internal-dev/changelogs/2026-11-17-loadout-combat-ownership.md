# Loadout and combat ownership

## Date
2026-11-17

## Git Commit

77e43d214bdbd3144e8729be5633090e52867d31

## Change Summary
Separate equipped-item ownership from attack state. `Loadout` owns equipment and selected weapon without holding `Combat`; `LoadoutSystem` coordinates guarded changes. `Combat` retains pending and active attack state and the active attack snapshot.

## Files
- `core/src/main/java/io/mindspice/lute/engine/components/{Loadout,Combat}.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/{LoadoutSystem,CombatSystem,CharacterCompositor,EntityFactory}.java`
- `core/src/main/java/io/mindspice/lute/engine/screen/GameScreen.java`
- `core/src/test/java/io/mindspice/lute/engine/{combat/CombatSystemTest,systems/MapSensorSystemTest}.java`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/{CompositeGameTest,CompositeRenderTest}.java`
- `docs/asset-service.md`, `.internal-dev/specifications/{composite-characters,decisions}.md`, `.internal-dev/knowledge/composite-sprite-assets.md`, `.internal-dev/reviews/2026-11-16-composite-character-pr-targets.md`.

## Behavioral Impact
Guarded gameplay switches use the installed `LoadoutSystem`; pending or active attacks reject changes before selection validation or mutation. Idle unknown IDs throw without mutation; null selects unarmed. Combat is optional for switches and composition. The component retains its immutable catalogue and equipment validator; its setters validate selections but do not guard attack state. `CombatSystem` requires Loadout and cleans up attack sensors when it is removed.

Focused `./gradlew :core:combatSystemTest :core:mapSensorSystemTest :lwjgl3:compileTestJava --console=plain` passed (1,800 combat and 1,732 map checks; `/tmp/lute-loadout-focused.log`). The filtered full check and real-GL run passed: `DISPLAY=:0 ./gradlew check :lwjgl3:compositeRenderTest :lwjgl3:compileJava -x :core:inputSystemTest --console=plain` (`/tmp/lute-loadout-full-filtered.log`), including 15,815 gameplay checks, 480 actual-asset/native-world comparisons across two weapon pages, and 11,854 synthetic GL checks. The unfiltered `--continue` run exited 1 only on the known pre-existing input movement-event failure, issue #6, after 1,235 checks; other full checks and GL passed (`/tmp/lute-loadout-full-unfiltered.log`).

## Specification Impact
Updated the composite-character ownership and switch contract and recorded the durable division of responsibility in `decisions.md`.

## Risks
Callers using component setters rather than `LoadoutSystem` operations bypass the attack-state guard. Other GL drivers and live visual approval remain unverified. Both asset trees remain ignored and locally provisioned; this follow-up did not generate assets or change their layout.

## Follow-up Items
Resolve issue #6 separately and rerun the unfiltered check. Review the PR update before publishing; publication is not a merge. Live visual review remains pending.
