# Internal development record creation

## Topic
Tool-managed Git evidence in changelogs.

## Source References
- `../AGENTS.md`
- `../changelogs/2026-11-17-loadout-combat-ownership.md`
- `internal_dev` create validation on 2026-11-17

## Key Takeaways
When supplying changelog content to `internal_dev`, leave the required `## Git Commit` section unfilled. The tool rejects an already populated section, even when its hash matches HEAD, and injects the current full Git hash itself. Verify the generated evidence after creation. On a dirty worktree this identifies the baseline, not the later implementation commit.

## Project Relevance
The ownership-refactor changelog initially failed validation with a supplied baseline hash. Retrying with an empty Git Commit section created the record and injected `77e43d214bdbd3144e8729be5633090e52867d31` correctly.

## Open Questions
None.
