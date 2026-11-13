# Box2D combat sensor lifecycle

## Topic
Runtime attack sensor fixtures on sleeping Box2D bodies.

## Source References
- `core/src/main/java/io/mindspice/lute/engine/systems/CombatSystem.java`
- `core/src/test/java/io/mindspice/lute/engine/combat/CombatSystemTest.java`

## Key Takeaways
Creating an attack sensor on a sleeping owner must wake the body so contacts are discovered. Fixture creation and removal must occur outside the locked world step. Regression tests exercise the sleeping-owner case using native Box2D.

## Project Relevance
Directional weapon sensors are attached temporarily to the player's body during attacks.

## Open Questions
None.
