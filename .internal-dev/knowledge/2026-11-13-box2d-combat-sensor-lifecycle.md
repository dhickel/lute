# Box2D combat sensor lifecycle

## Topic
Attack sensor fixtures attached to Box2D bodies.

## Source References
- `core/src/main/java/io/mindspice/lute/engine/systems/CombatSystem.java`
- `core/src/test/java/io/mindspice/lute/engine/combat/CombatSystemTest.java`

## Key Takeaways
Creating an attack sensor on a sleeping owner must wake the body so contacts are discovered. Create and remove fixtures outside the locked world step; defer contact damage processing until the world is unlocked.

## Project Relevance
Directional weapon sensors are attached temporarily to the player's body during attacks.

## Open Questions
None.
