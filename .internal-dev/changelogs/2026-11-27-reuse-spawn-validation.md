## Date
2026-11-27

## Git Commit

aa34ce06aa79ad92b42ca622ea7cec07d1262d92

## Change Summary
Reuse validated immutable weapon catalogs for equivalent spawn inputs and remove per-frame live atlas replacement checks.

## Files
EntityFactory, animation and compositor code, asset/combat/desktop tests, and asset lifecycle guidance.

## Behavioral Impact
Actor state remains independent. Unload and reload rebuild consumers; live replacement is not supported.

## Specification Impact
Documents validation reuse and asset lifetime boundaries.

## Risks
Callers must stop atlas consumers before replacing a shared atlas.

## Follow-up Items
Combat, asset lifecycle, map travel, Hoard, lifecycle, and isolated compositor checks passed. The full composite melee contact assertion also fails on baseline.
