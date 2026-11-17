## Date
2026-11-17

## Git Commit

cdaba3c091cdd7ebb9acd48d4947aeb74ec77844

## Change Summary
Move the startup keyboard bindings to `assets/input.setting.json`, next to the local game settings, and seed logical controller bindings for the same movement and attack actions.

## Files
- `assets/input.setting.json` and `.gitignore`: track the default input configuration without tracking other local assets.
- `GameCore.java` and `InputSettings.java`: load and validate the configuration before creating the input manager.
- `InputSettingsTest.java`, `InputSystemTest.java`, and `core/build.gradle`: exercise the shipped bindings, remapping, and invalid configuration in the input regression suite.
- `docs/input-system.md` and the internal input records: document the file format, validation, and controller scope.

## Behavioral Impact
WASD, Space, and either Shift key behave as before by default. Keyboard changes take effect on restart. Invalid or missing configuration stops startup with a file-specific error. The controller map does not drive gameplay yet.

## Specification Impact
The durable decisions record the input file contract. Deferred features track controller hardware integration.

## Risks
Custom configuration errors stop startup; the shipped defaults and invalid-input cases are exercised in the regression suite. `./gradlew check :lwjgl3:jar --console=plain -Dorg.gradle.logging.level=lifecycle` passed with 1,307 input checks and no failed groups. The desktop JAR contains the exact tracked configuration. Interactive desktop input was not tested.

## Follow-up Items
Connect logical controller identifiers to the controller backend when hardware support is implemented.
