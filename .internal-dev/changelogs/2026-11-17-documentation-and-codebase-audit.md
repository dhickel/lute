# Documentation refresh and full-codebase audit

## Date
2026-11-17

## Git Commit

33dbd077ff1c66bf211971086b77caf2c8800563

## Change Summary
Six specialist reviewers audited internal documentation, code correctness and architecture; root independently verified critical findings and ran isolated generation/core/util/GL checks. Refreshed stale knowledge and input documentation. Produced separate bugs/issues and architecture/simplification reports and mirrored three new defect reports to GitHub (#16, #17, #18). Existing issues #6 and #8 remain open. The Git hash above identifies the reviewed baseline, not a commit containing these audit edits.

## Files
- `.internal-dev/knowledge/point-player-spawning.md`: current JSON-based logical spawning and historical keyFrames incident.
- `.internal-dev/knowledge/tiled-collision-properties.md`: current class-based physics and nested animation properties; historical flag diagnosis distinguished from current advice.
- `.internal-dev/knowledge/screen-lifecycle.md`: corrected record path and historical smoke-run scope.
- `.internal-dev/knowledge/composite-sprite-assets.md`: isolated asset setup, explicit GL validation, snapshot-dependent atlas/check evidence.
- `docs/input-system.md`: actual factory/controller/physics/attack path, string-keyed screen cache and known baseline test failure.
- `.internal-dev/reviews/2026-11-17-bugs-and-issues.md`.
- `.internal-dev/reviews/2026-11-17-architecture-and-improvements.md`.
- `.internal-dev/bugs/{game-screen-retained-systems,runtime-generation-missing-sprites,desktop-java-runtime-mismatch}/report.md`.

## Behavioral Impact
Documentation and tracking only. No application code, assets, dependency versions or build configuration changed. Existing dirty tracked/untracked util/build outputs were preserved. Generated assets and test output were confined to an isolated /tmp checkout.

## Specification Impact
Specification Impact: none. All living specifications were reviewed; no confirmed intended-contract change was needed. Existing two-page current asset statements match fresh generation; historical seven-page validation remains explicitly snapshot-specific rather than being rewritten.

## Risks
The audit does not prove absence of defects. Packaging launch and native use-after-dispose were not executed; those findings are source/bytecode supported. Physical input and pending live visual bounds acceptance were not performed. The unfiltered full check remains failing on known issue #6, not passed or silently skipped.

## Follow-up Items
- Independent runtime generation passed.
- Unfiltered check failed only known controller movement-event issue #6; filtered check excluding that task passed with exit 0.
- Explicit GL harness passed: 15,815 gameplay checks, 480 actual-asset/native-scale comparisons over two weapon pages and 11,854 synthetic real-GL checks.
- Review and address GitHub #16 (screen systems lifetime), #17 (missing sprite input) and #18 (Java bundle mismatch), plus existing #8 (audio subscriptions) and #6 (movement-event contract).
- Optional bounded readability targets and their required regression coverage are in the separate architecture report; none was implemented.
