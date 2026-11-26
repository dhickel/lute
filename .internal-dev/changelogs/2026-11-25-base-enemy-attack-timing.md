# Set reusable base enemy attack timings

## Date
2026-11-25

## Git Commit

f0b44fb4be0db361c640dd982c0f204027102da8

## Change Summary
Expose reusable melee and archer engage-graph suppliers. Set the base melee decision delay to 50–250 ms per eligible attack opportunity.

## Files
`BehaviorGraphs.java`, `ActorAi.java`, focused awareness and live arena tests, and the related specifications and knowledge.

## Behavioral Impact
Archers keep their 150–350 ms decision delay and halfway aim lock. Melee windup, recovery, weapon cooldowns, and player timing are unchanged.

## Specification Impact
Documents the base AI timing and the archer supplier's `BowAim` requirement.

## Risks
Melee enemies can commit up to 100 ms sooner; existing windups remain readable at their previous duration.

## Follow-up Items
None.
