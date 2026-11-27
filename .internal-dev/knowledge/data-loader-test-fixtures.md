## Topic
Independent classpath fixtures for gameplay data tests

## Source References
`DataLoaderTest.java`, `core/src/test/resources/data-loader/`, `AGENTS.md`, and [#50](https://github.com/dhickel/lute/issues/50).

## Key Takeaways
Use distinct synthetic inputs through the real loader to catch swapped or reused attack slots and lost cycle order. Keep live catalog size and tuning out of loader tests. Resolve test resources through the test class; the default LibGDX `FileHandle` lookup did not find resources in the Gradle test classpath.

## Project Relevance
Running from an empty working directory verifies independence from local assets. Mutations that sort the weapon cycle or reuse the primary attack should make the tests fail.

## Open Questions
None for fixture lookup.
