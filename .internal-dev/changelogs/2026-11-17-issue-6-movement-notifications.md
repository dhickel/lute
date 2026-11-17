# Issue 6: correct stale movement-event test

## Date
2026-11-17

## Git Commit

aa8069dbc3cd91cccb8190f197955b8e52f46b24

## Change Summary
The earlier issue #6 restoration of press-only `Event.Player.Moved` emission is superseded. The user approved keeping emission removed and correcting the stale test instead. The test now expects no movement events while checking direction changes and attack routing.

## Files
- `core/src/test/java/io/mindspice/lute/engine/input/InputSystemTest.java`
- `docs/input-system.md`
- `.internal-dev/knowledge/input-listeners.md`
- `.internal-dev/specifications/decisions.md`
- `.internal-dev/bugs/.archive/controller-movement-event-test-failure.md`
- `.internal-dev/reviews/2026-11-17-bugs-and-issues.md` (local audit reference follows the archived report)

## Behavioral Impact
`ControllerSystem` remains identical to the base: it updates movement direction directly and emits no movement event. The `Event.Player.Moved` type and `EventEmitter` marker remain. No new gameplay feature is added.

## Specification Impact
The cache-only input listener decision records the user-approved rule that controller movement updates direction without emitting `Event.Player.Moved`.

## Risks
The focused input test and unfiltered full check passed for the current revision. No interactive input validation was performed.

## Follow-up Items
The historical baseline failed at 1,235 checks with one failed group. For the current revision, `./gradlew :core:inputSystemTest :lwjgl3:compileJava --console=plain -Dorg.gradle.logging.level=lifecycle` passed: 1,286 input checks, zero failures. The unfiltered `./gradlew check --continue --console=plain -Dorg.gradle.logging.level=lifecycle` also passed: 21 actionable tasks, including 1,286 input checks with zero failures, 1,928 combat checks, and 1,732 map sensor checks. Prior combined five-fix validation and independent combat results exercised the superseded restoration and are historical, not validation of this revision; no current GL rerun is claimed. PR #19 merged as e663e2587be758d411abf63c3df41b4297597574; GitHub issue #6 is closed. Local integration preserves the earlier audit documentation while adopting the corrected non-emission contract and archived issue reference.
