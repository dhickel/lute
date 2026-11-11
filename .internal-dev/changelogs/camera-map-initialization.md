# Avoid event-time camera family lookup

## Date

2026-07-19

## Git Commit

d11bd1b3044461a847038af9b45bd00e5d95ba21

## Change Summary

Remove the immediate camera-family lookup and zero-delta processing from CameraSystem.setMap().

## Files

- core/src/main/java/io/mindspice/lute/engine/systems/CameraSystem.java

## Behavioral Impact

Map notifications update camera bounds only. Normal Ashley iteration processes camera entities after pending additions are committed between system updates, avoiding the empty-family startup exception.

## Specification Impact

Specification Impact: none. This corrects initialization timing without changing the map or camera contract.

## Risks

Validation used the user's current uncommitted Tiled/event refactor; that refactor is not included in this focused commit.

## Follow-up Items

Validation: timeout 25s ./gradlew lwjgl3:run loaded the map, dispatched Changed, and continued rendering at approximately 60 FPS without the prior exception. Exit 124 was the intentional smoke-test timeout. Map replacement cleanup remains outside this change.
