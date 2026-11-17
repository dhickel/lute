# Issue 17: require complete sprite inputs before runtime publication

## Date
2026-11-17

## Git Commit

aa8069dbc3cd91cccb8190f197955b8e52f46b24

## Change Summary
Reject a missing top-level sprite input directory rather than reporting successful incomplete runtime generation. Test fixtures now supply the required production sprite tree. Independent phase review: PASS.

## Files
- `util/src/main/java/io/mindspice/lute/engine/util/TexturePackerTool.java`
- `util/src/test/java/io/mindspice/lute/engine/util/TexturePackerToolTest.java`
- `.internal-dev/knowledge/composite-sprite-assets.md`
- `.internal-dev/bugs/.archive/runtime-generation-missing-sprites/report.md` (resolved audit record)

## Behavioral Impact
Missing or empty required sprite inputs fail without publishing fresh output or changing existing owned/unowned files. Successful complete generation remains supported; ownership guards are unchanged.

## Specification Impact
Specification Impact: none. This enforces the existing complete raw-tree and required shared-atlas contract in composite-characters.md.

## Risks
Fixture coverage uses small representative sprites; actual copied local input generation also passed, with all 29 owned outputs matching hashes. Final combined integration with #6/#8/#16/#18 passed the unfiltered full check, lifecycle 157, gameplay 15,815 / 480 comparisons, synthetic GL 11,854 and normal generation with 29 owned outputs. This does not include sibling fixes on this branch. The Git hash is the coordinating audit baseline; the fix branch starts at its code-equivalent parent 33dbd077ff1c66bf211971086b77caf2c8800563.

## Follow-up Items
Senior implementation and independent validator both passed texturePackerToolTest, actual local generation, manifest/byte preservation checks and diff whitespace checks. Merge-candidate validation against master including PRs #19 and #21 passed: unfiltered check (21 tasks, input 1,286, map sensors 1,732, required-sprite generator regressions) and actual runtime generation for one map. Logs: `/tmp/lute-pr22-merge-check.log`, `/tmp/lute-pr22-merge-generation.log`. PR #20 is not included. PR #22 merged as b65e0029b166b98afe2848448bc1ebf8bf09a279; GitHub #17 is closed and the local audit record is archived.
