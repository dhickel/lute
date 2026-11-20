# Box2D combat sensor lifecycle

## Topic
Attack sensor fixtures attached to Box2D bodies.

## Source References
- `core/src/main/java/io/mindspice/lute/engine/systems/CombatSystem.java`
- `core/src/test/java/io/mindspice/lute/engine/combat/CombatSystemTest.java`

## Key Takeaways
Creating an attack sensor on a sleeping owner must wake the body so contacts are discovered. Create and remove fixtures outside the locked world step; defer contact damage processing until the world is unlocked.

Weapon bounds offsets are pixel translations applied after rotation, not changes to the rotation origin. Test signed X/Y translations for every facing and bounds shape. Bounds constructor migrations also affect qualified `WeaponData.Bounds` calls and LWJGL3 render fixtures; compile both core and LWJGL3 test sources rather than searching only `new Bounds` and `new CombatWeapon.Bounds`.

Normalizing the entire trajectory against post-aim travel can crush near-shot height. The latest playtest favored one unbroken world-Y parabola through a reachable crosshair, but short, steep shots traveled faster than configured when speed described projection along the aim line. Keep geometry separate from playback: use lift `min(arc_height, 0.15 * aim distance)` and continue the same parabola beyond the aim point, while advancing at constant arc-length speed. Centered aim instead travels straight along the owner's facing. Test short versus long shots for world-distance speed, as well as exact crosshair passage, unchanged geometry, and the clipped final collision sweep; long-update collision tests should cover the short pre-aim arc too.

## Project Relevance
Directional weapon sensors are attached temporarily to the player's body during attacks.

## Open Questions
None.
