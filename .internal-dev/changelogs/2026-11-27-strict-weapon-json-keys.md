## Date
2026-11-27

## Git Commit

aa34ce06aa79ad92b42ca622ea7cec07d1262d92

## Change Summary
Reject duplicate keys throughout weapon JSON using the same stream-level check as the actor loader and editor.

## Files
`DataLoader.java` and `DataLoaderTest.java`.

## Behavioral Impact
Valid catalogs are unchanged; ambiguous definitions now fail loading.

## Specification Impact
None. Aligns the loader with the editor’s existing validation.

## Risks
Previously accepted duplicate keys must be removed from malformed packs.

## Follow-up Items
Focused duplicate-key and valid-catalog regressions passed. Broader loader fixture failures reproduce on unchanged master.
