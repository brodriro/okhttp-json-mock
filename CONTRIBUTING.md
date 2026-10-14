# Contributing

Thanks for helping. Bug reports, ideas and pull requests are all welcome.

## Reporting a bug or proposing a change

Open an [issue](https://github.com/brodriro/okhttp-json-mock/issues/new/choose). For a bug, the most
useful things are the request (method and URL), the names of your mock files and the log line the
interceptor printed, which says which files it looked for.

For anything larger than a small fix, open an issue before writing code, so the approach can be
agreed first.

## Setting up

You need JDK 17 or newer. The sample app also needs an Android SDK with platform 36, pointed to by
`ANDROID_HOME` or `local.properties`. To work on the library alone you can skip it and use the
`:okhttp-json-mock` tasks.

```sh
./gradlew :okhttp-json-mock:test    # library tests
./gradlew build                     # everything the CI checks
```

## Branches and pull requests

- `develop` is the integration branch. Create your branch from it (`feature/short-description`) and
  open the pull request against `develop`.
- `master` only changes on a release.
- Keep a pull request to one subject. The template asks what changed and how you tested it.

## Commits

One commit per change, with a message of the form `[type] Description` in English, where type is:

- `fix` for a bug fix,
- `added` for a new feature, file or dependency,
- `change` for anything else: refactors, updates, build, documentation.

```
[fix] Avoid division by zero when minDelay equals maxDelay
[added] Resolve mocks by HTTP method
```

## What the build checks

`./gradlew build` runs what the CI runs:

- **Tests.** New behaviour needs a test. The tests drive the interceptor through a real
  `OkHttpClient` with an in-memory provider.
- **Code style**, with ktlint. `./gradlew ktlintFormat` fixes most problems.
- **Public API.** The API is recorded in `okhttp-json-mock/api/okhttp-json-mock.api` and the build
  fails if it changes. If you changed it on purpose, run `./gradlew :okhttp-json-mock:updateKotlinAbi`
  and commit the result. Removing or changing existing API is a breaking change and waits for the
  next major version.
- **Coverage.** The build fails under 85% line coverage of the library.
- **Android lint and R8** on the sample app.

The CI also builds `consumer-test`, a separate project that uses the published library with several
Kotlin versions. To run it yourself:

```sh
./gradlew :okhttp-json-mock:publishToMavenLocal
./gradlew -p consumer-test run
```

## Compatibility rules

The library is used from Java and from old Kotlin compilers, so:

- It targets Java 8 and is compiled with Kotlin language and API version 2.0. Do not use newer
  language features or standard library functions.
- It must keep working with OkHttp 4.12 and with 5.x.
- Mock files that work today must keep working: do not change which file answers a request.

## Documentation

If you change behaviour or the public API, update the [README](README.md) and add a line to the
[changelog](CHANGELOG.md).

## License

By contributing you agree that your contribution is licensed under the
[Apache License 2.0](LICENSE), like the rest of the project.
