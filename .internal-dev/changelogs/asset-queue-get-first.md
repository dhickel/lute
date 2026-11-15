# Use getFirst for the loading queue

## Date

2026-11-14

## Git Commit

815a4fd9cfab45c8d51d51f79a360f43902a904e

## Change Summary

Commit the user's change from `queue.iterator().next()` to `queue.getFirst()` in `AssetService.update()`.

## Files

`core/src/main/java/io/mindspice/lute/engine/assets/AssetService.java`.

## Behavioral Impact

No behavior change: the existing empty check guards access to the first queued entry.

## Specification Impact

None. Loading order and lifecycle remain unchanged.

## Risks

Requires the sequenced collection API supported by the project's Java 25 toolchain.

## Follow-up Items

The preceding `:core:assetServiceTest --offline` run passed with this exact user edit present. Diff whitespace and global policy synchronization checks passed before committing. Unrelated untracked reviews remain excluded.
