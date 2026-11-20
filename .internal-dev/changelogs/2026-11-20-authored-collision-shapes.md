## Date

2026-11-20

## Git Commit

9bb4ec740fa54a6e40d11d881d6aa661c5423bcb

## Change Summary

Generalized authored collision for players, actors, objects, and projectiles to support shapes, bounds, and rotation.

## Files

Collision data and fixture code, projectile sweep and debug rendering, focused tests, asset documentation, and living specifications.

## Behavioral Impact

The five ignored JSON files require migration. Existing entries use ellipses with zero rotation; the iron arrow changes from a triangle to an ellipse. Hitboxes, Tiled collision, arrow trajectory, and art remain unchanged.

## Specification Impact

Updated collision authoring, projectile geometry, and object-spawning contracts.

## Risks

Other local asset trees require migration. The arrow’s changed collision shape can alter contacts.

## Follow-up Items

Provision and regenerate assets on other machines. Manual gameplay review and the full project suite were not run for this revision. Asset generation and ten focused Gradle checks passed.
