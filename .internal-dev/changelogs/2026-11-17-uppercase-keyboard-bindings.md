## Date
2026-11-17

## Git Commit

69693fec38a7dc9a287e3866d6e2f84cba3544c0

## Change Summary
Use uppercase `Input.Keys` constant names in `input.setting.json` to match the style of the controller identifiers.

## Files
- `assets/input.setting.json`: use `SPACE`, `SHIFT_LEFT`, and `SHIFT_RIGHT` in the shipped defaults.
- `InputSettings.java` and `InputSettingsTest.java`: resolve constant names and test display-label rejection, non-key constants, and duplicate keycode aliases.
- `docs/input-system.md` and internal input records: describe the new authoring contract.

## Behavioral Impact
The physical bindings and controller map are unchanged. Custom files using display labels must switch to constant names.

## Specification Impact
The input settings decision now requires case-sensitive LibGDX key constant names.

## Risks
Old draft configurations with display labels fail startup. Gradle `check :lwjgl3:jar` passed with 1,312 input checks and no failures, and the packaged input settings match the source file byte for byte.

## Follow-up Items
None.
