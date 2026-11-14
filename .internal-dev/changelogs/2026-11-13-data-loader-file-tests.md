# Data loader file tests

## Date
2026-11-13

## Git Commit

61f3e055de0e154d664ca92cbab397412718460c

## Change Summary
Added checks that load player and weapon data, corrected the weapon filename, and deserialize the weapon map into the existing WeaponsData record. Updated the weapon assertion to check the bounds_type enum.

## Files
- `core/src/test/java/io/mindspice/lute/engine/data/json/DataLoaderTest.java`
- `core/build.gradle`
- `core/src/main/java/io/mindspice/lute/engine/data/json/DataLoader.java`
- `core/src/main/java/io/mindspice/lute/engine/data/json/WeaponsData.java`

## Behavioral Impact
The Gradle check runs the data loader test. Weapon loading reads `weapons.json` and returns its named records in a map.

## Specification Impact
None; this fixes the implementation to load the existing weapon data format.

## Risks
The loader return type is now `WeaponsData`, matching the existing wrapper record.

## Follow-up Items
None.
