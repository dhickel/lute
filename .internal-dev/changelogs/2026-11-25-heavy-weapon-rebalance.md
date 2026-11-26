# Rebalance axes and hammers

## Date
2026-11-25

## Git Commit

7dd7ab9e93872917c689ee459ad620f6fc7cc619

## Change Summary
Tuned axe and hammer timing and damage in both attack slots. Updated the live Dredge test to check windup against loaded weapon timing.

## Files
Local raw and generated `data/weapons.json`, and `lwjgl3/src/test/java/io/mindspice/lute/engine/systems/HoardArenaTest.java`.

## Behavioral Impact
Axe primary contacts in 325–367 ms with a 450 ms cooldown and damage of 38–44 basic or 45–51 black. Hammer primary contacts in 350–367 ms with a 550 ms cooldown and damage of 47–55 basic or 56–64 black. Bounds and cancellation rules are unchanged.

## Specification Impact
None. The existing timing and cancellation contracts are unchanged.

## Risks
Tested axe reach remains direction-dependent at 48 px; both slots landed in every facing at 40 px in the same fixture.

## Follow-up Items
Playtest the tuned weapons at ordinary combat distances.
