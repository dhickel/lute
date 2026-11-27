## Summary

`TiledSystem.loadMap` stores the loaded TiledMap under `mapAsset`, while `setMap` later reads that property as MapAsset.

## Scope

Map reload/unload bookkeeping, not the missing initial tree collision bodies. Discovered during the tree collision investigation; no repair applied.

## Reproduction

Source-level reproduction: call `loadMap` successfully, then load another map. `setMap` retrieves the previous map's `mapAsset` as MapAsset, although `loadMap` assigned the TiledMap itself.

## Expected

The previous map's asset identity is a MapAsset that AssetService can unload.

## Actual

The property contains TiledMap, which is incompatible with the requested MapAsset type.

## Evidence

`core/src/main/java/io/mindspice/lute/engine/systems/TiledSystem.java`: `loadMap` executes `tiledMap.getProperties().put("mapAsset", tiledMap)`; `setMap` calls `currMap.getProperties().get("mapAsset", MapAsset.class)`.

## Impact

Subsequent map loads can fail during asset unloading. Initial tree-body omission has a separate asset property-scope cause.

## Status

Open; source-confirmed mismatch, not executed in game. GitHub: https://github.com/dhickel/lute/issues/1. No matching open or closed GitHub issue found by a mapAsset issue search on 2026-11-10.

## Next Action

Store the MapAsset argument and validate successive map loads in a separately authorized fix.
