# Issue 16: remove screen-owned systems before World disposal

## Date
2026-11-17

## Git Commit

aa8069dbc3cd91cccb8190f197955b8e52f46b24

## Change Summary
Track GameScreen's installed system instances, detach/dispose them in reverse order, and destroy the World last. Preserve unrelated engine systems. Independent phase review: PASS; teardown continues across cleanup failures and aggregates them.

## Files
- `core/src/main/java/io/mindspice/lute/engine/screen/GameScreen.java`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/GameScreenLifecycleTest.java`
- `lwjgl3/build.gradle`
- `.internal-dev/knowledge/screen-lifecycle.md`

## Behavioral Impact
Disposed game screens no longer leave their physics systems updating a destroyed World. Hide/show reuse remains supported; disposal is idempotent and partial construction is cleaned up. Foreign system instances are not disposed.

## Specification Impact
Specification Impact: none. Implements resource ownership consistent with the existing custom-screen lifecycle; no screen-management features are introduced.

## Risks
Audio subscription internals remain independently scoped to issue #8. Cleanup failures are reported after attempting the remaining cleanup. The coordinating Git hash identifies the audit baseline; fix branch starts at code-equivalent parent 33dbd077ff1c66bf211971086b77caf2c8800563.

## Follow-up Items
Independent real-GL lifecycle task passed 157 checks, native map sensors 1,732, combat 1,928 for that run, trigger debug rendering, compilation and compositeRenderTest (11,854 synthetic GL checks) passed. Native combat counts may vary by run. Full integration is recorded separately. Issue #16 remains open until PR merge; no merge performed.
