## Date
2026-11-27

## Git Commit

c753a429ac8171c7fa18530545ce88d9be92f695

## Change Summary
Force the Hoard combat demo’s enemy-infighting chance to zero independently of normal game settings.

## Files
- `GameScreen.java`: demo-only chance override.
- `HoardHammerDemoTest.java`: verify melee and arrow retaliation stay disabled with the normal chance set to 100.
- `.internal-dev/specifications/decisions.md`: demo override contract.

## Behavioral Impact
Demo enemies do not retarget each other in response to accidental hits. Collateral damage and scored Hoard settings are unchanged.

## Specification Impact
Clarifies the combat demo’s isolated retaliation rules.

## Risks
None identified.

## Follow-up Items
None. The desktop Hoard Hammer Demo integration test passed.
