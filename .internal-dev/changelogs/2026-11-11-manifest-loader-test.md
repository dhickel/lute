# Manifest loader test

## Date
2026-11-11

## Git Commit

7ae2d1f7825b211db8d7b1a460c90e1ba7f1df91

## Change Summary
Added a manifest loader test and a Gradle `JavaExec` task to run it.

## Files
- `core/src/test/java/io/mindspice/lute/engine/assets/tiled/ManifestLoaderTest.java`
- `core/build.gradle`

## Behavioral Impact
The test parses the actual `test_tiled` manifest and checks for nonempty map, atlas, music, and sound references and descriptors without hardcoded asset counts.

## Specification Impact
None. The test does not change application behavior or its intended contract.

## Risks
The standard Gradle task requires project compilation, which the user reports is currently incomplete.

## Follow-up Items
Run the Gradle task after the existing compilation failures are repaired.

## Validation
The isolated `javac`/`java` run passed using the loader and its direct dependencies.
