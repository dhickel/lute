# Issue 18: align desktop bundle tools and runtimes with Java 25

## Date
2026-11-17

## Git Commit

aa8069dbc3cd91cccb8190f197955b8e52f46b24

## Change Summary
Select Java 25 for Construo host tools and all four bundled target runtimes while retaining the application's Java 25 language/release target. Independent phase review: PASS.

## Files
- `lwjgl3/build.gradle`
- `.internal-dev/knowledge/desktop-packaging.md`

## Behavioral Impact
Bundles use Corretto 25 JDKs for Linux x64, macOS ARM64/x64 and Windows x64. Host jlink derives from the configured toolchain rather than Gradle's Java 21 daemon. Corretto supplies the target jmods required by Construo 2.2.2; the tested Temurin 25 archive did not.

## Specification Impact
Specification Impact: none. Repairs runtime compatibility with the existing Java 25 compilation target without changing gameplay or packaging features.

## Risks
Latest URLs intentionally remain unpinned. Cross-platform runtime images were built and inspected, but only Linux was packaged and launched. Native-access/Unsafe warnings were nonfatal. Coordinating Git hash identifies the audit baseline; fix branch starts at code-equivalent parent 33dbd077ff1c66bf211971086b77caf2c8800563.

## Follow-up Items
Independent validation passed official URL/documentation checks, HTTP 200, all four archive hashes/releases/native architectures, Linux package task, and packaged launcher smoke. Bundled Java 25 libjvm was verified through proc maps with invalid JAVA_HOME; assets loaded and rendering reached 62 FPS. Class major version 69 confirmed. Other three runtime images built successfully and report Java 25.0.5; no cross-OS launch claim. Merge-candidate validation against master including PRs #19/#21/#22 passed: unfiltered check (21 tasks), Linux package generation, and a fresh ZIP extraction/launch with invalid JAVA_HOME proving the bundled Java 25 libjvm, asset loading and rendering. Logs: `/tmp/lute-pr23-merge-check.log`, `/tmp/lute-pr23-merge-package.log`, `/tmp/lute-pr23-merge-smoke.log`. Other OS launch tests were not rerun. PR #20 is not included. Issue #18 remains open until PR merge. Final combined integration with #6/#8/#16/#17 passed the unfiltered full check, lifecycle 157, gameplay 15,815 / 480 comparisons, synthetic GL 11,854 and normal generation. This does not imply sibling fixes are included in this branch.
