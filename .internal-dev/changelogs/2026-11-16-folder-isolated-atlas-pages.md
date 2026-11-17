# Folder-isolated sprite atlas pages

## Date

2026-11-16



## Git Commit

b975f30c5ef643daf48c78b4d925402c722674ee



## Change Summary

Isolate sprite source folders on atlas pages while preserving existing sprite and map packing contracts.

## Files

- `util/src/main/java/io/mindspice/lute/engine/util/TexturePackerTool.java`
- `util/src/test/java/io/mindspice/lute/engine/util/TexturePackerToolTest.java`

## Behavioral Impact

Sprites reject `combineSubdirectories=true`; map objects still require it. Equipment is gathered by slot and set. Current weapons use separate 2048×2048 sword and bow pages; character and four equipment families use one page each. Overflow can still split a folder and its animations. Defaults remain 2048 with explicit overrides and partial JSON inheritance.

## Specification Impact

The composite character asset contract now specifies folder isolation and the overflow limitation.

## Risks

Future oversized folders may split animations. Existing path, index, dimensions, rotation, transparent-frame and transactional guards remain in force.

## Follow-up Items

Recheck folder and animation page membership after source or packing changes.

## Validation

`:util:texturePackerToolTest` passed folder isolation, fitting sets, overflow, stale-page shrink, rejected sprite and map settings, source pixels, accessory preservation, 2048 defaults and transaction preservation. Current generated outputs have seven sprite pages and 29 owned hashes; all current eight-frame sequences stay on one page. The real GL audit passed 15,814 gameplay checks including 480 native comparisons across both weapon pages and 11,807 synthetic checks.
