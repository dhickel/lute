# Desktop gamepad support

## Date
2026-11-17

## Git Commit

97dc18f52e747687f8aed1661735e6f087f12ad3

## Change Summary
Added standard-mapped gamepad input alongside keyboard gameplay input.

## Files
`assets/input.setting.json`, the core input settings, mapping and polling classes, `GameScreen`, the core input tests, `docs/input-system.md`, and the input specifications.

## Behavioral Impact
One supported gamepad owns input until disconnect. Configured D-pad, face buttons, and digital left-stick directions feed the existing gameplay queues. Selection and lifecycle resets suppress controls held at the first sample until release.

## Specification Impact
Updated the input settings decision and removed the implemented controller hardware integration entry from deferred features.

## Risks
Polling can miss a complete tap between samples. Separate bindings to the same action produce separate pairs. No physical gamepad was attached during validation.

## Follow-up Items
Validate with a physical gamepad when available. The isolated clean build passed with 1,365 input checks. The Linux desktop jar initialized Controllers, reached gameplay, and sustained approximately 62 FPS until the intentional 18-second timeout.
