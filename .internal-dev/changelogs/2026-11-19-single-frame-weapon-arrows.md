# Single-frame arrows in the weapons atlas

## Date
2026-11-19

## Git Commit

d0ba8405226bdb9d8b7229807462c0935678e867

## Change Summary
Confirmed that the existing recursive weapons packer includes single-frame PNGs without requiring animation sequences. Added a regression test; no production change is necessary.

## Files
- `util/src/test/java/io/mindspice/lute/engine/util/TexturePackerToolTest.java`

## Behavioral Impact
The fixture packs `weapons/ranged/arrows/arrow.png` and `fire_arrow_00.png` into the common weapons atlas alongside sword and bow. It verifies static index -1, indexed frame 0, relative region names, source-folder isolation, canvas dimensions, orientation, transparent padding, visible pixels, deterministic regeneration, and unchanged source bytes. Actual arrow PNGs were not present in the local raw assets tree at validation time; none were fabricated or added. Projectile execution remains deferred.

## Specification Impact
Specification Impact: none. Existing recursive weapon packing already supports these files; this change adds regression coverage without changing runtime or generation contracts.

## Risks
The regression uses synthetic source images. Real arrow art cannot be regenerated until its PNGs are supplied under `raw_assets/sprites/weapons/`.

## Follow-up Items
Place arrow PNGs under the weapons source tree and run `./generate-runtime-assets.sh` to publish them. Focused validation passed: `./gradlew :util:texturePackerToolTest`.
