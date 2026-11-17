# Character canvas and configurable bounds

## Date

2026-11-16



## Git Commit

f78ea0b3492ac6f19d09f902e2325ceff43f348b



## Change Summary

Render full-size character canvases around the configured logical area and separate physical feet from damage-receiving hurtboxes. Restore the supplied 128×128 source sprites and regenerate the owned runtime atlases.

## Files

- `raw_assets/sprites/`, `assets/atlases/`, `assets/.runtime-assets-owned`: source frames and generated atlas outputs.
- `raw_assets/data/player.json`, `assets/data/player.json`: logical size and fitted bounds.
- `raw_assets/data/weapons.json`, `assets/data/weapons.json`: user-approved live weapon tuning preserved in source and regenerated runtime data.
- `core/src/main/java/io/mindspice/lute/engine/components/`, `data/json/`, and `systems/`: character geometry, composition, rendering, and combat contacts.
- `core/src/test/java/io/mindspice/lute/engine/` and `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/`: data, combat, map sensor, and GL coverage.
- `README.md`, `docs/asset-service.md`, `.internal-dev/specifications/composite-characters.md`, `.internal-dev/specifications/decisions.md`, and `.internal-dev/knowledge/composite-sprite-assets.md`: contract and authoring guidance.

## Behavioral Impact

The entire 128×128 canvas renders at native pixel scale around a centered 64×64 logical area, preserving padding and overhang. The 24×10 feet ellipse handles physical movement and map triggers; a separate tagged 32×50 sensor receives damage. Explicit hurtbox targets ignore physical fixtures for damage, while generic targets retain their fallback. Weapon, audio, and input production behavior is unchanged.

The latest supplied inventory contains 1,280 PNGs, all 128×128; 160 cast frames remain removed. The generated ownership manifest has 49 matching hashes: seven weapon pages and four pages for each other character atlas layer.

After senior advice, the user explicitly authorized preserving current live tuning and regenerating. The existing `basic_sword` tuning has primary speed 2, secondary speed 2.5, and secondary bounds x=48..72; it was not weapon tuning invented for this feature. The runtime data was backed up byte-for-byte at `/tmp/lute-live-tuning-reconcile-jsoMcj`, its prior owned-manifest hash was verified against the baseline, and the approved tuning was copied into raw source before regeneration. After confirming edits were stable, only the runtime weapon file was temporarily restored to the verified baseline and the normal generator ran without adoption flags or hash forgery. Final raw and runtime weapon data exactly matched the saved snapshot; among assets, only the manifest changed, and all 49 ownership hashes validated. The ownership guard was not weakened.

The user subsequently supplied a second corrected asset import while final validation was running. The older validator was cancelled to avoid checking stale hashes. The new exact pre-generation hash snapshot is `/tmp/lute-canvas-latest-before.sha256`; these latest source hashes were preserved through the successful normal generator run logged at `/tmp/lute-latest-sprites-import.log`. Older source hashes legitimately changed with the user's correction and are not claimed to match this snapshot. Raw and runtime player and weapon JSON remain identical, including the approved weapon tuning.

## Specification Impact

The composite character specification and canvas decision describe the user-approved logical size, bounds, rendering, and contact contract. The initial fitted bounds remain subject to live review and tuning.

## Risks

Automated checks do not replace live assessment of visual scale and fitted contacts. The unrelated input baseline issue #6 remains.

## Follow-up Items

Obtain the user's live visual review before updating PR #15; visual approval has not been given. Keep branch `fix/character-canvas-and-hitboxes` local, without push, merge, or PR update.

## Validation

Earlier focused automated validation passed independently: 480 full-scale frame comparisons against actual sources, 14,285 game checks, 6,880 synthetic GL checks, 1,803 combat checks, 1,732 map checks, and data and compilation checks. After the latest import, focused and full tests were rerun. The unfiltered full check exited 1 only on known input issue #6 (movement: expected 1, got 0); see `/tmp/lute-latest-assets-full-unfiltered.log`. The full check excluding only `:core:inputSystemTest` exited 0; see `/tmp/lute-latest-assets-full-filtered.log`. Latest checks included 13,613 game checks, including 480 actual composition/native-world frame comparisons across all seven pages; 6,880 synthetic GL checks; 1,795 combat checks (dynamic counts vary); and 1,732 map checks. All 49 ownership hashes matched, and the latest 1,280 PNG hashes were preserved against the new pre-generation snapshot. The earlier 14,285 count remains historical evidence, not the final count.

The inspected bounds screenshot at `/tmp/lute-canvas-bounds-review.png` shows green feet and a red hurtbox; inspection is not user live approval. The linked [canvas and hitboxes validation report](../reviews/2026-11-16-character-canvas-hitboxes-validation.md) records earlier validation evidence; the latest refresh and rerun are recorded above. The older composite integration report is historical evidence for prior work, not this correction.
