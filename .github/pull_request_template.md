## Summary

<!-- What does this pull request do, and why? -->

## Type of change

- [ ] `fix` — bug fix
- [ ] `added` — new feature, file or dependency
- [ ] `change` — change to existing behaviour, refactor, build or documentation

## Changes

<!-- The main changes, one per line. -->

-

## How it was tested

<!-- Commands you ran and what you checked. Delete the lines that do not apply. -->

- [ ] `./gradlew :okhttp-json-mock:test`
- [ ] `./gradlew build`
- [ ] `./gradlew :okhttp-json-mock:publishToMavenLocal && ./gradlew -p consumer-test run`
- [ ] Sample app run on a device or emulator

## Breaking changes

<!-- Anything a consumer of the library has to change. Write "None" if there are none. -->

## Checklist

- [ ] The base branch is `develop` (or `master` for a release)
- [ ] Commits are atomic and follow `[fix|added|change] Description`
- [ ] New behaviour is covered by tests
- [ ] README and `CHANGELOG.md` are updated when the public API or behaviour changes
- [ ] `okhttp-json-mock/api/okhttp-json-mock.api` is updated (`updateKotlinAbi`) when the public API changes
