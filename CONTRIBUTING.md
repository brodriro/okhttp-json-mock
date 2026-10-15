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
./gradlew ktlintFormat              # fix code style
./gradlew :app:installDebug         # install the sample app on a device or emulator
```

To run one test class or one test:

```sh
./gradlew :okhttp-json-mock:test --tests 'io.github.brodriro.okhttpjsonmock.MockFileResolverTest'
./gradlew :okhttp-json-mock:test --tests '*OkHttpMockInterceptorTest.envelope*'
```

The coverage report is written to `okhttp-json-mock/build/reports/kover/html/index.html` by
`./gradlew :okhttp-json-mock:koverHtmlReport`, and the API reference to
`okhttp-json-mock/build/dokka/html` by `./gradlew :okhttp-json-mock:dokkaGeneratePublicationHtml`.

## Project layout

```
okhttp-json-mock/         the library, published to Maven Central
  api/                    recorded public API, checked by the build
  src/main/kotlin/        sources, package io.github.brodriro.okhttpjsonmock
  src/test/kotlin/        JUnit 5 tests
app/                      Android sample app, uses the library as a project dependency
  src/main/assets/        its mock files, also run by the library's SampleMocksTest
consumer-test/            separate Gradle build that uses the library from mavenLocal, like a real project
gradle/libs.versions.toml versions of every dependency and plugin
.github/workflows/        CI, release, snapshot and API reference workflows
jitpack.yml               build of preview versions on JitPack
```

## How the code is organised

| File | Role |
| --- | --- |
| `OkHttpMockInterceptor.kt` | The `Interceptor` and its `Builder`. Picks the mock, waits, decides on a simulated failure, logs, records and builds the response. |
| `MockFileResolver.kt` | Turns method, path and query into the ordered list of candidate files, `{id}` variants included. Internal. |
| `MockEnvelope.kt` | Reads the `statusCode` / `headers` / `delayMs` / `response` wrapper with Gson. Internal. |
| `ReasonPhrases.kt` | Reason phrase of every status code for the status line. Internal. |
| `MockLogger.kt` | The one-line log of each call, with `STDOUT` and `NONE`. |
| `MissingMockException.kt`, `RecordedCall.kt` | Test options: `failOnMissingMock()` and `recordCalls()`. |
| `providers/` | `InputStreamProvider`, the extension point, and `DefaultInputStreamProvider` for the filesystem. |

A change to which file answers a request goes in `MockFileResolver` and its test,
`MockFileResolverTest`. A change to what a mock file can say goes in `MockEnvelope` and
`MockEnvelopeTest`. Everything else is usually in the interceptor, tested end to end in
`OkHttpMockInterceptorTest` through a real `OkHttpClient` and an in-memory provider:

```kotlin
val provider = FakeProvider(mapOf("api/v1/users/1.json" to USER))

val response = clientFor(provider).get("http://example.com/api/v1/users/1")

assertEquals(200, response.code)
```

`FakeProvider` also records every path it was asked for, which is how the lookup order is tested.

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

- **Tests.** New behaviour needs a test. Most tests drive the interceptor through a real
  `OkHttpClient` with an in-memory provider; the resolver, the wrapper and the reason phrases also
  have unit tests of their own.
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
- Anything that is not meant for users stays `internal`. The library is compiled with `explicitApi`,
  so every public declaration needs an explicit visibility. Give it KDoc too: it becomes the API
  reference.

## Documentation

If you change behaviour or the public API, update the [README](README.md) and add a line to the
[changelog](CHANGELOG.md), under the version in development. Examples in the README should match a
test or a mock of the sample app, so they keep working.

## Releases

Releases are made by the maintainer from `master`: a `vX.Y.Z` tag builds, signs and uploads the
artifacts to the Maven Central portal, where the maintainer publishes them, and publishes the API
reference. Pushes to `develop` publish a snapshot
while the version ends in `-SNAPSHOT`. A published version can never be replaced, so a mistake is
fixed with a new patch version.

## License

By contributing you agree that your contribution is licensed under the
[Apache License 2.0](LICENSE), like the rest of the project.
