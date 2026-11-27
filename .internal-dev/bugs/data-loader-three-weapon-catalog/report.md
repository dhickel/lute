## Summary
`DataLoaderTest` expects exactly three weapons in the shared catalog, but the current Hoard catalog contains twelve.

## Scope
Unrelated to debug overlays; tracked in [GitHub #50](https://github.com/dhickel/lute/issues/50).

## Reproduction
Run `./gradlew :core:dataLoaderTest --offline` with the current provisioned arena assets.

## Expected
Catalog validation should match the current supported assets.

## Actual
`DataLoaderTest.weaponSchema` fails with `AssertionError: shared catalog`.

## Evidence
`DataLoaderTest.java:397` checks the size against 3; `jq '.weapons | length' assets/data/weapons.json` returns 12.

## Impact
The data loader task stops before later assertions.

## Status
Open.

## Next Action
Reconcile fixture expectations with the current catalog without dropping coverage of required weapon IDs.
