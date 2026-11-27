## Date
2026-11-27

## Git Commit

34930691e9424ea4ccbc691f41c9f13e14c6f7ff

## Change Summary
Replace weapon-ID-specific attack handling with required authored attack animation keys. Generalize lunge and twirl and migrate Dredge to choose the shared hammer's secondary. Rename Hammer Demo to Test Area and isolate its rules from scored Hoard.

## Files
- Combat, animation, AI, arena, screen, and weapon data under `core/src/`.
- Integration tests under `lwjgl3/src/test/`, plus the weapon tester parser and tests.
- Weapon asset guide, composite-character specification, and design decisions.

## Behavioral Impact
Each attack slot owns its move, timing, damage, and geometry. Test Area retains its existing menu position and is the unscored mode for interactive weapon testing, using the same combat implementation as ordinary gameplay.

## Specification Impact
Documents the authored attack schema, Dredge's secondary decision, and the boundary between Test Area and normal modes.

## Risks
Ignored asset catalogs are local to each checkout. Other checkouts must migrate both attack slots to `anim_key`, set Dredge's authored behavior graph, and regenerate runtime assets.

## Follow-up Items
Combat, awareness, data, Hoard, Test Area, lifecycle, travel, composite rendering, and weapon tester checks passed.
