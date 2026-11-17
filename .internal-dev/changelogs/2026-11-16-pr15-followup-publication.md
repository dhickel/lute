# PR 15 follow-up publication

## Date

2026-11-16

## Git Commit

3efe9863e464d88015737d4fe7e3c5ea56994e25

## Change Summary

Prepare the PR #15 description and code-only target review for the user-authorized publication of the three local follow-up commits covering character canvas and bounds, set-first equipment exports, and the supersampled sprite refresh. Publication has not yet been performed.

## Files

- `.internal-dev/reviews/2026-11-16-composite-character-pr-targets.md`: current code-only review inventory.
- `.internal-dev/changelogs/2026-11-16-pr15-followup-publication.md`: this publication preparation record.
- `/tmp/lute-pr15-updated-body.md`: draft PR description for root review and publication; not a repository file.

## Behavioral Impact

Metadata and review text only; the described runtime and asset changes were already implemented in the local commits. This record does not itself push, update or merge the PR.

## Specification Impact

None: the changes revise a review and publication description, not the implemented contract.

## Risks

The unfiltered check remains failing on pre-existing issue #6. Accessory exports are skipped and not equippable; bow projectile execution remains deferred. Live visual approval is not attested.

## Follow-up Items

Root to review and publish the authorized PR update. PR review remains open; this work does not authorize merging into master. Resolve issue #6 separately.

## Validation

- `DISPLAY=:0 ./gradlew check :lwjgl3:compileJava :lwjgl3:compositeRenderTest --continue --console=plain`: exit 1, only the pre-existing `:core:inputSystemTest` movement failure (`expected 1, got 0`); `/tmp/lute-pr15-update-unfiltered.log`.
- `DISPLAY=:0 ./gradlew check -x :core:inputSystemTest :lwjgl3:compileJava :lwjgl3:compositeRenderTest --continue --console=plain`: exit 0; `/tmp/lute-pr15-update-filtered.log`. Reports 13,613 gameplay checks, including 480 actual composition and native-world frame comparisons across seven weapon pages; 6,880 synthetic real-GL checks; 1,795 combat checks (counts vary); 1,732 map checks; and passing data, utility and remaining checks.
- Earlier repeat generation was byte-identical, preserved all 1,472 source PNG hashes and verified all 49 owned hashes; `/tmp/lute-supersampled-repeat-generation.log`. Raw/runtime player and weapon data matched.
- `git diff --check`: passed. Global policy `cmp`: passed.
