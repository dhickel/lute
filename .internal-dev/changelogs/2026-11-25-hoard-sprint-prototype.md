# Prototype Hoard sprint and stamina

## Date
2026-11-25

## Git Commit

b2c3e92fd4810f66ade4641fd2d31af437fe72e1

## Change Summary
Prototype hold-to-sprint on `hoard-mode-sprint-test`, with health and stamina centered on a second HUD row. The dash and backpedal experiments remain separate.

## Files
Hoard input, movement, animation, HUD and tests; `README.md`; Hoard sprint decision; enemy detection bug report.

## Behavioral Impact
Holding controller B enables 1.6× movement and 2× walk animation while advancing. Full stamina lasts two seconds and refills from empty in about 8.57 seconds. Exhaustion requires releasing and pressing B again. Ordinary walking collision behavior is unchanged.

## Specification Impact
Records the branch-local sprint and stamina contract.

## Risks
No physical-controller playtest was performed. Broader catalog, composite contact, and lighting checks reproduced their baseline failures.

## Follow-up Items
Playtest sprint as a dodge option. Data-driven enemy detection shape is tracked separately in [issue #48](https://github.com/dhickel/lute/issues/48).
