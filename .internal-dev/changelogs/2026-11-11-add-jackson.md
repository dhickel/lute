# Add Jackson JSON dependency

## Date
2026-11-11

## Git Commit

9916351a547675989edd0f090a5649840c87bcab

## Change Summary
Added Jackson 3.3.0 databind as an implementation dependency of core. Jackson core and annotations resolve transitively.

## Files
- `core/build.gradle`

## Behavioral Impact
JSON serialization and deserialization APIs are available for future manifest implementation. No asset loading behavior changed.

## Specification Impact
Specification Impact: none. This adds a requested library without changing existing application contracts.

## Risks
Jackson 3 uses `tools.jackson.databind` imports rather than Jackson 2's `com.fasterxml.jackson.databind` imports. Existing unchecked Java compiler warnings remain.

## Follow-up Items
None required for dependency installation. Manifest serialization is not part of this change.

## Validation
Passed `./gradlew :core:compileJava :core:dependencyInsight --dependency jackson-databind --configuration runtimeClasspath`; runtime dependency resolves to Jackson databind 3.3.0.
