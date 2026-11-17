# Full-codebase bugs and issues review

## Scope
Six read-only reviewers covered documentation/specifications, gameplay correctness, input/assets/screens/maps, JSON/tooling/desktop boundaries, and architecture. Repository baseline: `33dbd077ff1c66bf211971086b77caf2c8800563`. Root independently inspected critical findings and ran isolated validation. No application source or build configuration was changed.

The team inventoried the complete three-module source tree (core, util, lwjgl3), covered every production package, inspected associated tests and authored map/data metadata, and audited all ten knowledge files and all living specifications. Gameplay and architecture reviews traced the main cross-cutting flows rather than reading every leaf/test line. Binary images/audio were not exhaustively visually/audibly reviewed. This is broad source and executable-test coverage, not proof that every possible defect is absent.

## Findings
### Confirmed new defects
| Priority | Finding and exact target | Trigger and consequence | Evidence / tracking |
| --- | --- | --- | --- |
| P1: bundled distribution | `lwjgl3/build.gradle` Construo targets select Temurin 21; root build and desktop compile target Java 25 | Launch a bundle using its configured runtime: Java 21 cannot load Java 25 bytecode. Local Gradle execution on Java 25 is unaffected. | Root verified desktop class major version 69 with javap; bundled JDK URLs select 21.0.10. No packaging download/launch attempted. [Issue #18](https://github.com/dhickel/lute/issues/18), [local report](../bugs/.archive/desktop-java-runtime-mismatch/report.md). |
| P2: screen lifecycle | `core/.../screen/GameScreen.java:100–107`, `systems/PhysicSystem.java:27–40` | Unload/dispose GameScreen while the shared engine survives, then update it: registered systems retain a disposed World; PhysicSystem unconditionally calls clearForces. | Source-confirmed ownership failure. Native crash intentionally not reproduced in the main process. [Issue #16](https://github.com/dhickel/lute/issues/16), [local report](../bugs/.archive/game-screen-retained-systems/report.md). |
| P2: generation boundary | `util/.../TexturePackerTool.java:294–301` and generate/publication flow | Supply valid map/data/settings/audio inputs but omit the entire sprites folder: generation reports success without required shared atlases; owned previous sprite output may be removed during publication. | Required subfolder validation is bypassed by the top-level early return; existing fixture generator tests accept this shape. [Issue #17](https://github.com/dhickel/lute/issues/17), [local report](../bugs/.archive/runtime-generation-missing-sprites/report.md). |

Full source prefixes: `core/src/main/java/io/mindspice/lute/engine/` and `util/src/main/java/io/mindspice/lute/engine/util/`.

Subsequent disposition: issues #16, #17 and #18 were resolved by merged PRs #21, #22 and #23 respectively; their local records are archived. The findings above describe the audited baseline. PR #20 / audio issue #8 remains open. Issue #6 was resolved separately by merged PR #19 correcting the stale assertion without restoring movement-event emission.

### Existing unresolved issues (at audit baseline)
- **Audio subscriptions outlive system/screen ownership**: `systems/AudioSystem.java:24–29` registers captured consumers without corresponding unsubscription. Removing/discarding the screen leaves subscriptions active. Existing [issue #8](https://github.com/dhickel/lute/issues/8) and [local report](../bugs/audio-system-event-subscription-lifecycle/report.md); not duplicated.
- **Controller movement-event test/implementation mismatch**: `InputSystemTest.controllerMovementEvent` expects an event that ControllerSystem does not emit. Independently reproduced: `press emits once: expected 1, got 0`; 1,235 checks, one failed group. Existing [issue #6](https://github.com/dhickel/lute/issues/6) and [local report](../bugs/.archive/controller-movement-event-test-failure.md). The desired contract still needs reconciliation; this audit does not choose whether test or implementation should change.

### Conditional concerns, not promoted to confirmed current gameplay defects
- `screen/ScreenManager.java:23–25`: duplicate loadScreen key displaces the previous screen without disposal; if it is active, cache and current-screen ownership diverge. No current duplicate-key caller found. Define and test duplicate-key policy before changing it.
- `systems/EventSystem.java:76–88`: a throwing listener interrupts cleanup and can replay previously dispatched queued events on the next update if the caller catches and continues. No ordinary listener-throw path demonstrated; exception recovery policy is unspecified.
- `tiled/MapTriggers.java:36–40`: absent orientation is accepted while explicitly non-orthogonal values are rejected. Normal authored maps declare orientation; omission was not proven to violate the current public contract, so this remains a malformed-input question, not a confirmed defect.
- Parsed detector polygon arrays remain mutable after list copying; no current mutating caller found. Manually assembling a mismatched map/atlas logical ID pair can also violate loader assumptions, but current manifest construction aligns them. No speculative internal defensive framework is recommended.

### Documentation and asset-state observations
Knowledge contained current-tense claims about removed keyFrames/player properties, a removed flag-based Tiled loader, and old API paths. Those were corrected separately. Living intended specifications were reviewed and retained: no confirmed contract drift justified rewriting them.

Historical canvas/occlusion reviews describe seven weapon pages, while this audit's raw-input generation produced two. Both historical records and current evidence are retained as snapshot-specific; no historical result was silently overwritten. The current specification's two-page statement matches the current generation. Original local assets were incomplete (missing settings/project/data), so copying them alone was insufficient; generation in the isolated checkout resolved those setup failures.

## Risk Assessment
The packaging incompatibility is deterministic for the configured bundles. The disposed-World issue is serious but requires engine activity after screen disposal, not the normal shutdown-only path. Missing-sprite validation can invalidate generated resources despite success. No new confirmed bug was established in combat timing, loadout switching, compositor ordering, input queue semantics, or geometry algorithms during this review; this is not an assertion that those areas are defect-free.

## Recommendations
1. Align packaged Java runtime and compilation target; validate a packaged launch.
2. Remove screen-owned systems/listeners in a defined order before destroying their World; add unload/recreate lifecycle tests.
3. Reject missing production sprite inputs before publication and test preservation of prior outputs on invalid input.
4. Resolve existing audio subscription ownership and movement-event mismatch without conflating them with documentation updates.
5. Keep conditional concerns distinct from reproducible defects; prioritize real screen/resource lifecycle coverage over speculative validation inside trusted internals.

## Follow-ups
### Independent validation, 2026-11-17
Validation ran against archived HEAD in `/tmp/lute-audit-fJbNLP`, using independent copies of local raw inputs and regenerated runtime output. Original dirty util/build outputs were untouched.

| Check | Result |
| --- | --- |
| `./generate-runtime-assets.sh --console=plain` | PASS; complete output, two weapon pages. |
| `./gradlew check --continue --console=plain` | FAIL only at known `:core:inputSystemTest` issue #6. All other registered checks passed. |
| `./gradlew check -x :core:inputSystemTest --continue --console=plain -Dorg.gradle.logging.level=lifecycle` | PASS, independently captured exit 0; excludes only known issue #6. |
| CombatSystemTest | PASS: 1,928 checks, native Box2D. |
| MapSensorSystemTest | PASS: 1,732 checks. |
| MapTriggersTest | PASS: 134 rejection cases. |
| AssetServiceTest, DataLoaderTest, ManifestLoaderTest, TiledAnimationPropertiesTest, TriggerDebugRenderSystemTest | PASS. |
| TexturePackerToolTest | PASS: ownership, stale outputs, collision refusal, rollback, path/symlink and multi-map cases. |
| `DISPLAY=:0 ./gradlew :lwjgl3:compositeRenderTest --console=plain -Dorg.gradle.logging.level=lifecycle` | PASS: CompositeGameTest 15,815 checks, 480 actual-asset/native-scale comparisons, two weapon pages; CompositeRenderTest 11,854 real-GL checks. |
| Desktop class inspection | Java 25 class major version 69 confirmed; bundled Java 21 mismatch confirmed statically. |

Temporary logs: `/tmp/lute-audit-generation.log`, `/tmp/lute-audit-final-check.log`, `/tmp/lute-audit-filtered-check.log`, `/tmp/lute-audit-gl.log`. These are supplemental ephemeral logs; this table is the durable evidence summary. GL initialization emitted a libdecor plugin warning but both real-GL harnesses passed. Gradle check alone does not execute those desktop harnesses. No physical-key interaction, user visual acceptance, packaged launch, exhaustive binary asset audit, or unsafe native-disposal reproduction was performed. New defect reports were mirrored to GitHub and checked against related closed issues (#1 and #2); neither closes the new findings.
