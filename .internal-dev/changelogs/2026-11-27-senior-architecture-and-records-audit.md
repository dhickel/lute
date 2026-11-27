## Date
2026-11-27

## Git Commit

b6afcad7e40c63b9fa35252d202dca5b638ca587

## Change Summary
Record four senior architecture and records reviews, refresh living documentation, and archive the closed map-reload and superseded tree-atlas reports.

## Files
README, asset and input guides, living specifications and knowledge, architecture review and bug records under `.internal-dev/`.

## Behavioral Impact
None; no production code or assets changed.

## Specification Impact
Clarifies existing Test Area, attack animation, asset loading, input, and world-time contracts. Preserves the intended Hoard scoring contract pending investigation.

## Risks
Architecture findings are from static inspection; runtime reproduction is pending for newly tracked defects.

## Follow-up Items
Track transition rollback ([#52](https://github.com/dhickel/lute/issues/52)), terrain scaling ([#53](https://github.com/dhickel/lute/issues/53)), and Hoard score attribution ([#54](https://github.com/dhickel/lute/issues/54)).
