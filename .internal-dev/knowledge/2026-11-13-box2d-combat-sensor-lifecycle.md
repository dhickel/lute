# Box2D combat sensor lifecycle

## Topic
Attack sensor fixtures attached to Box2D bodies.

## Source References
- `core/src/main/java/io/mindspice/lute/engine/systems/CombatSystem.java`
- `core/src/test/java/io/mindspice/lute/engine/combat/CombatSystemTest.java`

## Key Takeaways
Creating an attack sensor on a sleeping owner must wake the body so contacts are discovered. Create and remove fixtures outside the locked world step; defer contact damage processing until the world is unlocked.

Weapon bounds offsets are pixel translations applied after rotation, not changes to the rotation origin. Test signed X/Y translations for every facing and bounds shape. Bounds constructor migrations also affect qualified `WeaponData.Bounds` calls and LWJGL3 render fixtures; compile both core and LWJGL3 test sources rather than searching only `new Bounds` and `new CombatWeapon.Bounds`.

## Project Relevance
Directional weapon sensors are attached temporarily to the player's body during attacks.

## Open Questions
None.
