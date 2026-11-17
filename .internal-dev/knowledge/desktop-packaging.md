## Topic

Desktop packaging: Java toolchains, target JDKs, and bundled-runtime validation.

## Source References

- `build.gradle` — Java 25 toolchain configuration.
- `lwjgl3/build.gradle` — Java 25 compilation and Construo `jdkRoot`, target archives, and packaging configuration.
- `.internal-dev/changelogs/2026-11-17-issue-18-java25-bundles.md` — issue 18 packaging change and validation record.

## Key Takeaways

- Compilation bytecode level, the host `jlink` version, and the target JDK's `jmods` are separate packaging inputs. Align all three with the intended Java runtime.
- The Gradle daemon JVM is not necessarily the configured Java toolchain. Explicitly supplying Construo's `jdkRoot` from the toolchain selects the host tools independently of the daemon.
- An official JDK archive is not sufficient by itself: verify it contains `jmods` for Construo. The tested Temurin 25 archive lacked them; the tested Corretto 25 archives contained them.
- A real packaged launch should prove the bundled `libjvm` is loaded, rather than merely succeeding with a system Java installation. An invalid `JAVA_HOME` and process maps helped establish this on Linux.
- Successfully linking a cross-OS runtime image does not prove that the complete package builds or launches on the target OS.

## Project Relevance

The `:lwjgl3:packageLinuxX64` task exercises Linux packaging. `:lwjgl3:createRuntimeImageMacM1`, `:lwjgl3:createRuntimeImageMacX64`, and `:lwjgl3:createRuntimeImageWinX64` exercise cross-target image creation, not native launches. The Corretto latest URLs are unpinned and may resolve to different archives later.

## Open Questions

- Do fully packaged macOS ARM64, macOS x64, and Windows x64 builds launch successfully on their respective operating systems?
