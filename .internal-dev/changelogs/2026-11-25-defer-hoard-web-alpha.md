# Defer the Hoard web alpha

## Date
2026-11-25

## Git Commit

137ea2378c3b3270b82faa0c6f7b740b81a20fd8

## Change Summary
Record the validated browser experiment and how to resume it when the project is ready for a web alpha.

## Files
- `.internal-dev/specifications/deferred-features.md`
- `.internal-dev/knowledge/html5-export.md`

## Behavioral Impact
None. The browser implementation stays on `hoard-mode-alpha-web`; no browser code or assets are integrated into `hoard-mode-alpha`.

## Specification Impact
Records web alpha as deferred rather than part of active desktop development.

## Risks
Prior browser validation was limited to the prototype and must not be treated as release validation.

## Follow-up Items
Reconcile the prototype with current Hoard changes and revalidate target browsers, input, and performance when web alpha work resumes.
