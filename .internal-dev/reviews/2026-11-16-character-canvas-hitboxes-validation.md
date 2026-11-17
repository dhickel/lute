# Character canvas and hitbox validation

## Scope

Final independent technical gate for `fix/character-canvas-and-hitboxes` at `f78ea0b3492ac6f19d09f902e2325ceff43f348b`, compared with the prior phased evidence and the current source/specification contract. The gate covered the requested canvas/logical-size separation, native-pixel composition, alignment and culling, configurable feet and hurtbox fixtures, combat contact routing, asset ownership, raw source preservation, documentation consistency, and the required full Gradle checks.

No commit, branch, merge, push, PR update, reset, clean, or source-code repair was performed. The existing `util/build` dirt and raw asset worktree changes were preserved. The previous composite-character review was not rewritten. User live review and approval remain pending.

## Findings

### Latest independent closeout acceptance and retry evidence

This closeout supersedes the stale-hash blocked verdict below for the current worktree. The earlier blocked history is retained, including both authorized import events:

1. The first authorized reconciliation preserved the user's live weapon tuning (`basic_sword` primary speed `2`, secondary speed `2.5`, secondary jab bounds `x=48..72`). Exact runtime bytes were backed up under `/tmp/lute-live-tuning-reconcile-jsoMcj`; only the runtime weapon file was temporarily restored to its verified owned baseline before the normal generator ran. The ownership guard was not weakened and no manifest was forged.
2. The second authorized import replaced the raw sprite set with the user's latest 128×128 PNGs and removed the 160 cast files. The cancelled validator's old snapshot was not reused. The current expected snapshot is `/tmp/lute-canvas-latest-before.sha256`, and the normal generator evidence is `/tmp/lute-latest-sprites-import.log`.

The latest source snapshot is stable and matches the expected snapshot exactly: 1,280 unique expected PNG paths, 1,280 current PNGs, zero cast files, and zero non-128×128 files. The raw and runtime `player.json` and `weapons.json` files are byte-identical. The current ownership manifest has 49 entries, all hashes validate, and the generated output has seven weapon pages. Current source inspection remains consistent with the contract: `EntityFactory` sizes the transform from configured logical 64×64 data while retaining the native canvas, `CharacterCompositor` and `RenderSystem` preserve the complete native canvas and transformed overhang, `CharacterFixtures` creates the non-sensor feet ellipse and tagged persistent hurtbox sensor, and `CombatSystem` routes explicit damage to the hurtbox while preserving generic fallback behavior.

Latest evidence timestamps are ordered correctly: raw source maximum mtime `2026-11-16 19:05:25.692637071 -0500`; generator log `19:06:47.051662883`; focused log `19:07:20.948673637`; unfiltered full log `19:08:10.925689493`; filtered full log `19:08:41.227699106`. The source maximum mtime precedes the latest root validation log. Current specifications, README, asset-service documentation, knowledge, and the latest changelog describe the same native-canvas, logical-size, bounds, ownership, and known-baseline contracts.

The latest normal generator completed successfully. The focused latest log passed `DataLoaderTest`, `CombatSystemTest` (1,787 checks), `MapSensorSystemTest` (1,732 checks), `CompositeGameTest` (13,613 checks including 480 actual-asset/native-scale comparisons across seven weapon pages), and `CompositeRenderTest` (6,880 real-GL checks). The latest unfiltered full gate exited 1 only at the known baseline `:core:inputSystemTest` failure (`press emits once: expected 1, got 0`); the gate excluding only that test exited 0. The latest full logs report `CombatSystemTest` 1,795 checks, `MapSensorSystemTest` 1,732 checks, `CompositeGameTest` 13,613 checks with 480 comparisons across seven weapon pages, and `CompositeRenderTest` 6,880 checks. No new failure was found.

### Requested implementation path

Source inspection and the focused evidence remain consistent with the contract:

- `EntityFactory` uses configured `logical_size` for the transform and stores the loaded native canvas dimensions separately in `CompositeCharacter`.
- `CharacterCompositor` renders the complete loaded canvas with native pixel dimensions, uses a transparent gutter, aligns all layer frames against the base canvas, includes death/action/cache paths, batches by actual texture page, and culls full transformed canvas bounds including overhang, rotation, and scale.
- `RenderSystem` draws the completed canvas around the logical transform center at `GameConsts.UNIT_SCALE`; it does not shrink the 128x128 source into the 64x64 logical area.
- `CharacterFixtures` converts the bottom-left logical bounds to world units once. The 24x10 feet ellipse is non-sensor. The 32x50 hurtbox ellipse is a persistent sensor tagged with `Hurtbox`.
- `CombatSystem` accepts explicit hurtbox contacts, excludes solid fixtures when a target body has a hurtbox, preserves generic-solid fallback, and rejects self, dead/removed, ordinary-sensor, and duplicate attack hits through the existing lifecycle checks.
- No production input, audio, NPC, UI, projectile-execution, or unrelated gameplay-scope expansion was introduced by the requested source diff.

The inspected bounds image `/tmp/lute-canvas-bounds-review.png` is 128x128 and shows the green feet fixture and larger red hurtbox in the expected logical placement. This is technical inspection only, not user live approval. The native-padding and transformed-overhang screenshots are `/tmp/lute-canvas-native-padding.png` and `/tmp/lute-canvas-overhang-rotated.png`.

### Historical blocked gate retained

The following subsection records the earlier cancelled/stale-hash gate and remains for audit history; it is not the current verdict.

- `DISPLAY=:0 ./gradlew check :lwjgl3:compileJava :lwjgl3:compositeRenderTest --continue --console=plain`: **FAIL, exit 1**, only at the known baseline `:core:inputSystemTest`. It reports `InputSystemTest: 1235 checks, 1 failed groups` and `press emits once: expected 1, got 0`. The exact log is `/tmp/lute-final-gate-full.log`.
- `DISPLAY=:0 ./gradlew check -x :core:inputSystemTest :lwjgl3:compileJava :lwjgl3:compositeRenderTest --continue --console=plain`: **PASS, exit 0**. The exact log is `/tmp/lute-final-gate-excluding-input.log`.
- The filtered run reports `CombatSystemTest: 1803 checks passed`, `MapSensorSystemTest: 1732 checks passed`, `CompositeGameTest: 14285 checks; 480 actual-asset composition AND native-scale world-frame comparisons; 7 weapon pages sampled`, and `CompositeRenderTest: 6880 real GL checks passed`.
- The known input failure was not masked or repaired.

### Historical asset-ownership evidence retained

This subsection records the earlier source snapshot and the old live-tuning block. Its raw snapshot was intentionally superseded by `/tmp/lute-canvas-latest-before.sha256` after the user's second authorized import.

- `sha256sum --check --strict --quiet /tmp/lute-canvas-raw-before.sha256`: **PASS, exit 0** for all 1,280 expected PNG hashes.
- Raw inventory count: **1,280 PNGs**.
- Independent PNG header check: **1,280 files, 0 dimension failures**; every source is 128x128.
- The raw diff contains exactly **160 deleted cast files** and no current cast files.
- `cmp raw_assets/data/player.json assets/data/player.json`: passed during the gate.
- `git diff --check`: **PASS**.
- `cmp /home/dhickel/AI/Workspaces/Pi-extensions/global-AGENTS.md /home/dhickel/.pi/agent/AGENTS.md`: **PASS**.

The isolated generator validation, using a temporary copy of the raw source and an empty temporary output directory, passed:

- `./gradlew -I /tmp/lute-isolated-generator.init.gradle :util:generateRuntimeAssets --console=plain`: **PASS, exit 0**.
- Temporary generated ownership verification: **PASS**.
- Temporary `player.json` and `weapons.json` comparisons: **identical**.
- Temporary generated weapon pages: **7**.
- Temporary ownership entries: **49**.
- Exact log: `/tmp/lute-isolated-generator.log`.

The live repository output could not complete the same publication check because `assets/data/weapons.json` was being changed during the gate by the user live-review state. The requested generator command was run exactly as:

- `./raw_assets/generate-runtime-assets.sh --console=plain`: **FAIL, exit 1**, with `Refusing to replace modified owned asset: .../assets/data/weapons.json`; exact log: `/tmp/lute-final-generator.log`.
- At the independent contract check, `raw_assets/data/weapons.json` and `assets/data/weapons.json` differed in the basic sword secondary bounds (`32..56` in raw versus the live runtime edit `40..64`), and the ownership manifest reported the expected hash `bcf6daa4...` versus the current file hash `68aae722...`; exact evidence: `/tmp/lute-final-independent-contract-checks.log`.
- The generator refusal is a protection against replacing a modified owned asset. No repair or overwrite was performed.

This live runtime-data edit is outside the requested canvas/hitbox source repair and must remain under the user's control. It prevents a clean final worktree asset-consistency verdict even though isolated generation and the earlier full focused Gradle checks passed before the live edit.

### Documentation consistency

The current specification, knowledge, and changelog state the native 128x128 canvas contract, separate logical size and bounds, focused validation counts, the known input baseline issue, and `LIVE REVIEW PENDING`. The historical composite integration review remains unchanged. The final review is the only internal review record updated by this gate.

## Risk Assessment

- The requested implementation path has no defect found by the independent source inspection, latest asset checks, or latest filtered full validation.
- The unfiltered check remains red only because of the pre-existing controller movement-event test failure (`press emits once: expected 1, got 0`). This closeout did not change or mask that contract.
- The latest user-authored raw import is stable against `/tmp/lute-canvas-latest-before.sha256`; raw/runtime JSON and all 49 owned generated hashes match. The earlier modified-owned-runtime blocker is resolved by the two authorized reconciliation/import steps and is retained only as history.
- Automated checks do not replace the required user live visual review of the fitted bounds. No user approval is inferred.
- A pre-existing `Lwjgl3Launcher` process was observed and left untouched. No persistent game was intentionally launched or stopped by this closeout.

## Recommendations

1. Hand the stable technical result to the user for live visual review and bounds tuning; do not present that review as already approved.
2. If the user changes source assets or bounds during live review, take a fresh snapshot before validation and rerun only the affected focused checks and final asset ownership comparisons.
3. Keep the known `InputSystemTest.controllerMovementEvent` issue separate; do not repair or mask it in this follow-up.
4. Keep the branch local. The root may make the separately authorized local commit only; no merge, push, or PR update is authorized by this review.

## Follow-ups

- User live review and approval of the fitted bounds: **pending**.
- Preserve the latest raw snapshot, generator log, and full-gate logs as the current evidence set; do not substitute the cancelled validator's old snapshot.
- If live tuning changes the checked data, repeat the snapshot, generator/ownership, JSON comparison, and affected focused validation sequence.
- Preserve the known input baseline failure and all unrelated `util/build` and raw asset dirt.

## Historical Exact Commands and Results

| Command | Result |
|---|---|
| `DISPLAY=:0 ./gradlew check :lwjgl3:compileJava :lwjgl3:compositeRenderTest --continue --console=plain` | FAIL, exit 1; only `:core:inputSystemTest`, `/tmp/lute-final-gate-full.log` |
| `DISPLAY=:0 ./gradlew check -x :core:inputSystemTest :lwjgl3:compileJava :lwjgl3:compositeRenderTest --continue --console=plain` | PASS, exit 0; `/tmp/lute-final-gate-excluding-input.log` |
| `sha256sum --check --strict --quiet /tmp/lute-canvas-raw-before.sha256` | PASS, exit 0; 1,280 raw PNG hashes |
| `git diff --check` | PASS |
| `cmp /home/dhickel/AI/Workspaces/Pi-extensions/global-AGENTS.md /home/dhickel/.pi/agent/AGENTS.md` | PASS |
| `./raw_assets/generate-runtime-assets.sh --console=plain` | BLOCKED by modified owned `assets/data/weapons.json`, exit 1; `/tmp/lute-final-generator.log` |
| isolated `:util:generateRuntimeAssets` with temporary raw/output directories | PASS, exit 0; 7 weapon pages, 49 ownership entries; `/tmp/lute-isolated-generator.log` |
| `cmp raw_assets/data/player.json assets/data/player.json` | PASS at the independent check |
| `cmp raw_assets/data/weapons.json assets/data/weapons.json` | FAIL because of concurrent live-review tuning; `/tmp/lute-final-independent-contract-checks.log` |

## Historical Verdict (retained)

**VERDICT: BLOCKED**

The character canvas, logical-size, fixture, combat, rendering, raw-asset, and focused full-suite criteria pass. The earlier integration gate was blocked only because the then-current live worktree contained a concurrently changing modified owned `assets/data/weapons.json`, so the repository generator and final raw/generated JSON ownership checks could not be certified without overwriting user review state. This historical verdict does not describe the stable latest import. It does not claim user live approval, PR approval, merge authorization, or push authorization.

## Final Exact Commands and Results

| Command or evidence | Result |
|---|---|
| `sha256sum --check --strict --quiet /tmp/lute-canvas-latest-before.sha256` | PASS, exit 0; all 1,280 latest raw PNG hashes |
| Current sorted raw hash list compared with `/tmp/lute-canvas-latest-before.sha256` | PASS; no source-hash drift during closeout |
| Raw inventory and dimension checks | PASS; 1,280 PNGs, all 128×128, zero cast files |
| `cmp raw_assets/data/player.json assets/data/player.json` | PASS, exit 0 |
| `cmp raw_assets/data/weapons.json assets/data/weapons.json` | PASS, exit 0; approved tuning retained |
| 49-entry `assets/.runtime-assets-owned` checksum verification | PASS, exit 0; seven weapon pages present |
| Latest normal generator, recorded in `/tmp/lute-latest-sprites-import.log` | PASS, exit 0; generated character/equipment atlases and seven weapon pages |
| Latest focused validation, `/tmp/lute-latest-sprites-focused.log` | PASS; data, native Box2D, composition, and real-GL checks |
| `DISPLAY=:0 ./gradlew check :lwjgl3:compileJava :lwjgl3:compositeRenderTest --continue --console=plain` latest log `/tmp/lute-latest-assets-full-unfiltered.log` | FAIL, exit 1 only at known `:core:inputSystemTest` (`expected 1, got 0`) |
| `DISPLAY=:0 ./gradlew check -x :core:inputSystemTest :lwjgl3:compileJava :lwjgl3:compositeRenderTest --continue --console=plain` latest log `/tmp/lute-latest-assets-full-filtered.log` | PASS, exit 0; excludes only the known baseline test |
| Source maximum mtime compared with latest filtered root log mtime | PASS; source mtime `19:05:25.692637071 -0500` precedes root log `19:08:41.227699106 -0500` |
| `git diff --check` | PASS, exit 0 |
| `cd /home/dhickel/AI/Workspaces/Pi-extensions && cmp global-AGENTS.md /home/dhickel/.pi/agent/AGENTS.md` | PASS, exit 0 |

## FINAL VERDICT

**PASS — technical closeout criteria pass for the latest user-authored import.** The native 128×128/full-pixel canvas, centered logical 64×64 transform, authored feet and hurtbox bounds, sensor/combat routing, latest raw/runtime ownership, generator retry, focused checks, and latest filtered full gate are independently supported. The unfiltered full gate has only the known baseline input failure. User live bounds review remains required and pending; this report does not claim visual approval, merge, push, PR, or any weapon/bounds tuning approval.
