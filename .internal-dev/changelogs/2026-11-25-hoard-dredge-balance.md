# Make Dredge pursue bow attackers

## Date
2026-11-25

## Git Commit

5e74e1591d3822c812d6547108aa95b28303f193

## Change Summary
A damaging player arrow provokes the Hoard Dredge guard to pursue until solid body contact or the player is no longer live. Set Dredge’s authored base health to 300 and add 5 HP per round number for Hoard spawns. Reduce round packs to 250 HP.

## Files
Awareness, combat, Hoard arena logic and tests, README and related internal-dev records. Authored and generated enemy and object JSON are local ignored assets.

## Behavioral Impact
Dredge has 325 HP at round 5 and 350 HP at round 10. Range, cover and sensor overlap do not cancel arrow pursuit. Ordinary combat resumes on solid contact. Score, batch pacing and kill-drop packs are unchanged.

## Specification Impact
Updated the living Hoard and actor awareness contracts.

## Risks
Pursuit uses ordinary movement without pathfinding; solid terrain can still obstruct movement.

## Follow-up Items
None. Focused Hoard, awareness, combat and travel tests passed.
