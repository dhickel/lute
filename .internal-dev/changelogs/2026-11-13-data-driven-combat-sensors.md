# Data-driven combat sensors

## Date
2026-11-13

## Git Commit

61f3e055de0e154d664ca92cbab397412718460c

## Change Summary
Add data-driven player combat and directional weapon attack sensors.

## Files
Player and weapon data under `assets/data/`, combat and animation systems, input, physics integration, debug rendering, and focused tests.

## Behavioral Impact
Space starts a directional attack using a configured weapon shape; health and hit processing are tracked, and trigger and sensor geometry is available through debug rendering.

## Specification Impact
None: no existing living specification defines player combat.

## Risks
The pre-existing input-system movement-event check fails independently on the baseline.

## Follow-up Items
Review attack tuning and address the unrelated baseline input-event failure separately.
