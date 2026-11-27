## Date
2026-11-27

## Git Commit

9ebad18d8c0135c7c6be827c5d19c5b6db62d266

## Change Summary
Removed the unused configuration-time asset index writer and its directory-creation workaround.

## Files
`build.gradle`, `generate-runtime-assets.sh`, and composite sprite asset knowledge.

## Behavioral Impact
Ordinary resource processing no longer rewrites the live asset tree.

## Specification Impact
None; existing runtime ownership and publication contracts are unchanged.

## Risks
Repository consumer search cannot establish whether private external tools use the old index.

## Follow-up Items
Resource processing, failed publication isolation, generator tests, and a Linux bundle launch passed.
