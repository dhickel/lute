# Local asset directories and generation entrypoint

## Date

2026-11-16



## Git Commit

b975f30c5ef643daf48c78b4d925402c722674ee



## Change Summary

Stop tracking both local asset trees and relocate the generation entrypoint to the repository root.

## Files

- `.gitignore`, `generate-runtime-assets.sh`, and the formerly tracked `raw_assets/generate-runtime-assets.sh`.
- `assets/` and `raw_assets/` index entries; local files remain present.
- `README.md`, `docs/asset-service.md`, composite asset specification, decisions, knowledge and PR target review.

## Behavioral Impact

New checkouts must provide the complete raw tree externally and run `./generate-runtime-assets.sh`. The wrapper reports a missing source directory and creates the runtime directory before Gradle. Git ignore is independent of generator ownership. Historical Git objects are not purged.

## Specification Impact

The composite asset specification and durable local-tracking decision describe provisioning and ownership boundaries.

## Risks

The game and asset-dependent tests cannot work from a checkout without local data. Older branches that track these paths can overwrite local files; preserve and verify their hashes before branch changes.

## Follow-up Items

Provide source assets externally for each checkout; resolve the independently tracked input test failure separately.

## Validation

Index removal covered 1,603 paths without deleting local files; before and after filesystem snapshots matched all 1,604 local files. A clean isolated checkout failed actionably without sources and created no runtime directory, then successfully generated 29 valid owned hashes after raw-only provisioning. Evidence: `/tmp/lute-untracked-bootstrap.log`. The unfiltered full check failed only pre-existing `:core:inputSystemTest` (issue #6); excluding that task passed, with local assets provisioned.
