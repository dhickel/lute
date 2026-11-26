# Update Hoard vision and healing feedback

## Date
2026-11-25

## Git Commit

ed7acc80bfd410285b00239b57bce067e673ed3f

## Change Summary
Add forward enemy vision, sight-based Dredge pursuit, randomized round-health placement, and floating healing feedback on `hoard-mode-combat-test`.

## Files
Actor awareness and spawn setup, `HoardSystem.java`, gameplay health feedback and pickup handling, focused tests, README and affected internal-dev specifications and knowledge.

## Behavioral Impact
Ordinary enemy vision covers the forward semicircle. Dredge keeps full-circle sight; the Hoard guard can chase outside the health ellipse and returns home on detection loss. Round health pickups land inside the authored ellipse and clear of Dredge's spawn. Successful healing displays actual HP gained as rising, fading `+N HP` at twice the damage-number size.

## Specification Impact
Updates the Hoard and map-actor awareness decisions and the composite-character specification.

## Risks
Vision and pursuit still depend on the authored radius and solid cover. The branch's combat tuning is a playtest prototype.

## Follow-up Items
Playtest flanking, mixed waves, Dredge pursuit and recovery, and healing readability before promotion.
