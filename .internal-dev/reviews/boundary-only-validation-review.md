# Recent merge review for repeated internal validation

## Scope

Terra reviewed production code introduced or changed by recent PRs #4, #5, #7, and #9, focusing on combat, animation, asset access, and entity composition. Two bounded `openai-codex/gpt-6.1-terra` high-thinking runs performed the cleanup and atlas-reload follow-up. Root inspected both results, removed remaining internal asset-service null guards, avoided duplicate initial atlas validation, and independently validated integration. Implementation commit: `cc022b3`.

## Findings

- Runtime attack/weapon records, combat assignment, health operations, and asset lookups repeated checks on internally constructed or previously validated data. Remove those guards rather than maintaining multiple validation layers.
- Required player movement/health/weapon values and weapon bounds belong in the JSON records. Those records now reject invalid external values before runtime components are constructed.
- Hit and sound cue limits require loaded atlas information. `EntityFactory` validates all facing directions when composing an entity. Animation stores only the required frame count, not weapon definitions; it checks again only when an already observed atlas identity changes. Ordinary updates and attacks do not repeat the bounds checks.
- Native polygon validation needs the configured world scale and sprite anchor. Validate the geometry for all directions at entity composition, not each call generating attack vertices.
- The adopted user change routes animation sound requests through `EventEmitter`. Isolated tests bind and restore the singleton engine instead of bypassing that convention.

## Risk Assessment

Legitimate optional state, gameplay conditions, asset lifecycle, unsupported resource types, missing loaded resources, and loader failures remain checked. JSON/settings/asset-definition boundaries remain validated. Tests cover malformed player/weapon data, native-unsafe geometry, cue bounds, overflow-sized external cue indices, invalid replacement atlases, valid replacements, collision behavior, and attack cleanup. A plain internal record is no longer responsible for validating external data.

## Recommendations

Apply the root `AGENTS.md` boundary-only validation policy to further changes. Keep metadata-dependent validation at composition or external asset replacement, with no validation framework or repeated update-loop guards.

## Follow-ups

Passed independently: 218 native combat checks, data loader, asset service, manifest loader, Tiled animation properties, trigger debug checks, and desktop compilation. The full `./gradlew check lwjgl3:classes --continue` run still fails solely on independently established baseline input issue #6; no graphical launch was performed. Source changes after the full run were limited to removal of remaining internal asset constructor/queue null guards, followed by successful focused revalidation. No new unresolved findings remain in this review scope.
