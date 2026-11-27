## Date
2026-11-27

## Git Commit

aa34ce06aa79ad92b42ca622ea7cec07d1262d92

## Change Summary
Validate strict weapon catalog JSON and require the generator's canonical `weapons.atlas` name before packing.

## Files
`util/build.gradle`, `TexturePackerTool.java`, `TexturePackerToolTest.java`, and `docs/asset-service.md`.

## Behavioral Impact
Invalid catalogs fail generation without publishing changes. Runtime loading is unchanged.

## Specification Impact
Enforces the existing generated filename at the generator boundary.

## Risks
Raw packs specifying other atlas names must update their catalog before generating.

## Follow-up Items
Generator tests and isolated publication checks passed.
