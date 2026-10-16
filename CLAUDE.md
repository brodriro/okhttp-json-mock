# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A small JVM library that mocks HTTP responses for OkHttp/Retrofit: an OkHttp `Interceptor` short-circuits every request and answers with the contents of a local JSON file whose path mirrors the request URL. This repo is the `brodriro` fork of `mirrajabi/okhttp-json-mock`, continuing from 3.0 and openly presented as a fork.

## Modules

- `okhttp-json-mock/` — the library. Kotlin (`explicitApi`), Java 8 bytecode, language/API version 2.0 and `kotlin-stdlib` 2.0.21 on purpose (so consumers on Kotlin 1.9+ can read its metadata; do not use newer language features or stdlib APIs), published as `io.github.brodriro:okhttp-json-mock`.
- `app/` — Android sample app that consumes the library via `project(":okhttp-json-mock")`: a Jetpack Compose (Material 3) catalog with one screen per feature, each sending requests through the interceptor and showing status, headers, the mock file and the body (images decoded, PDFs rendered). `catalog/Catalog.kt` lists the features and their example requests, `mock/Playground.kt` builds the interceptor and runs them, `ui/` holds the screens, and the Retrofit `UsersService` is kept as one of the features. Its mock data lives in `app/src/main/assets/api/v1/...` and is also exercised by the library's `SampleMocksTest`; its instrumented tests in `app/src/androidTest` (`MockFeaturesTest`, `MockExtensionsTest`, `CatalogUiTest`) check every feature on a device, and `ReadmeSnippetsTest` / `ReadmeJavaSnippetTest` run the Android snippets of the README as they are written (keep them in sync), with the quick-start mock in `src/androidTest/assets`. Kotlin is compiled by AGP 9 itself; the only Kotlin plugin is the Compose compiler one. It compiles against SDK 37 (required by the Compose BOM) and targets 36.

- `consumer-test/` — standalone Gradle build, not included in the root project, that consumes the published artifact from `mavenLocal` from Kotlin and Java. CI runs it against several Kotlin versions.

## Commands

Toolchain: Gradle 9.8.1 (wrapper), AGP 9.4.1, Kotlin DSL build scripts, versions in `gradle/libs.versions.toml`. Needs JDK 17+ (21 is what is installed locally). `app` also needs an Android SDK (`ANDROID_HOME` or `local.properties`) with platform 37.

```sh
./gradlew :okhttp-json-mock:build                # build the library only
./gradlew :app:assembleDebug                   # build the sample app
./gradlew :app:installDebug                    # install the sample on a connected device
./gradlew :app:assembleDebugAndroidTest        # build the instrumented tests of the sample (run them on a device)
./gradlew :okhttp-json-mock:publishToMavenLocal  # jar + sources + javadoc + pom into ~/.m2
```

The library has JUnit 5 tests written in Kotlin under `okhttp-json-mock/src/test/kotlin`. Most drive the interceptor through a real `OkHttpClient` with an in-memory `InputStreamProvider`; `MockFileResolverTest`, `MockEnvelopeTest` and `ReasonPhrasesTest` test those classes directly.

```sh
./gradlew :okhttp-json-mock:test                                              # all library tests
./gradlew :okhttp-json-mock:test --tests 'io.github.brodriro.okhttpjsonmock.OkHttpMockInterceptorTest'
./gradlew build                                                             # compile + tests + ktlint + API and coverage checks + R8 + Android lint
./gradlew ktlintFormat                                                      # fix code style violations
```

Code style is checked by ktlint (`intellij_idea` style, 120 columns, set in `.editorconfig`) in both modules; `ktlintCheck` is part of `check`.

The public API is pinned by `okhttp-json-mock/api/okhttp-json-mock.api`. `check` fails when the API no longer matches it; after an intended change run `./gradlew :okhttp-json-mock:updateKotlinAbi` and commit the new dump.

`consumer-test/` is a standalone Gradle build, outside the root project, that uses the library from `mavenLocal` the way an outside project does (Kotlin and Java). Run it with `./gradlew :okhttp-json-mock:publishToMavenLocal && ./gradlew -p consumer-test run`; `-PkotlinVersion=` and `-PokhttpVersion=` select the consumer's compiler and OkHttp. Kotlin plugins older than 2.x need an older Gradle than the wrapper's, which is why CI runs it with Gradle 8.10.2.

CI lives in `.github/workflows`, with every action pinned to a commit SHA (Dependabot, configured in `.github/dependabot.yml`, proposes updates to `develop` weekly and is told not to bump OkHttp or JUnit to a new major):

- `ci.yml` runs on pull requests to `develop` and `master` and on pushes to `develop`. The `build` job runs `./gradlew build` and uploads the Kover HTML report; the `consumer` jobs run the consumer project against Kotlin 1.9.25, 2.0.21, 2.2.21 and 2.4.21 and against OkHttp 5; `CI passed` succeeds only when all of them did and is the one check meant for branch protection.
- `release.yml` runs on `v*` tags, fails if the tag does not match the library version and uploads to Maven Central using the `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY` and `SIGNING_KEY_PASSWORD` repository secrets. The upload still has to be published by hand in the Central Portal.
- `snapshot.yml` runs on pushes to `develop` and publishes to the Maven Central snapshot repository with the same secrets, but only while the version ends in `-SNAPSHOT`; with a release version it does nothing. Snapshots must be enabled for the namespace in the Central Portal.
- `docs.yml` runs on `v*` tags (or by hand) and deploys the Dokka HTML to GitHub Pages, which must have "GitHub Actions" as its source.

`check` also runs `koverVerify`, which fails under 85% line coverage of the library (it is around 99%), and the release build of the sample is shrunk with R8, so `./gradlew build` fails if the library ever needs keep rules it does not ship.

The library version is set in `okhttp-json-mock/build.gradle.kts` and must be kept in sync with the README (dependency snippet) and `CHANGELOG.md` on release. Publishing goes through the `com.vanniktech.maven.publish` plugin; signing is only enabled when a signing key is configured. The javadoc jar is generated by Dokka from the KDoc of the public API. `LICENSE` and `NOTICE` (attribution to the original project and summary of changes) are packaged into `META-INF` of the jar.

## Releasing

1. `version` in `okhttp-json-mock/build.gradle.kts` without `-SNAPSHOT`, README dependency snippet and `CHANGELOG.md` in sync, merged to `develop` with CI green.
2. Pull request `develop` → `master`, merged with a merge commit.
3. Tag `vX.Y.Z` on `master` and push it. `release.yml` uploads the signed artifacts to the Central Portal.
4. In the portal, wait for the deployment to be `Validated`, review the files and press Publish.

A published version is immutable: it cannot be replaced or deleted, so a mistake found afterwards needs a new patch version. Until Publish is pressed the deployment can be dropped and the workflow run again; a failed run can be re-run without a new tag. If the tag itself points at the wrong commit, delete it locally and on the remote and create it again before anything is published. After a release, `develop` moves to the next `-SNAPSHOT` version.

The library is compiled with language and API version 2.0 and `kotlin-stdlib` 2.0.21 so that consumers on Kotlin 1.9 and later can use it; the `consumer` job of the CI checks it on every pull request.

## How the interceptor resolves a request

`OkHttpMockInterceptor.intercept` (`io.github.brodriro.okhttpjsonmock`) asks `MockFileResolver.candidates(method, url)` for the files that can answer the request and uses the first one the provider has. For `GET /api/v1/users?page=1` the order is:

1. `api/v1/users/page=1.get.json` — this method, this query.
2. `api/v1/users.get.json` — this method, any query.
3. `api/v1/users/page=1.json` — any method, this query.
4. `api/v1/users.json` — any method, any query.

`Builder.extensions(...)` replaces the `.json` extension with a list (`extensions("json", "xml")`): every name above is tried with each extension, in the given order, before the next name, so a more specific name wins over the extension order. The response gets the `Content-Type` of the matched extension from the fixed table in `ContentTypes` (`application/octet-stream` for an unknown one), and only `.json` files are parsed as envelopes. The default list is `json`, which keeps the 4.0 resolution; each extra extension multiplies the provider calls.

The method suffix is the HTTP method in lower case (`.get`, `.post`, `.put`, `.delete`, `.patch`, …). Files without it answer every method, which keeps 3.0 mocks working and is how one URL gets a different mock per method.

When none of those exists, the same names are tried with `{id}` in place of a path segment (`api/v1/{id}.get.json`, `api/{id}/users.get.json`, …): one segment at a time starting from the last, then two at a time, never more than two. A file or folder literally named `{id}` therefore answers any value of that segment (`users/{id}.json`, `users/{id}/posts.json`), and a file for the exact path always wins. `basePath` is never replaced. Braces cannot clash with a real path because OkHttp percent-encodes them in URLs, and aapt packages `{id}` assets with its default settings (a leading underscore would be ignored for folders).

With several query parameters the exact string is tried first (`page=1&categoryId=5`) and then the same parameters sorted (`categoryId=5&page=1`), in both the method and the shared group. `basePath` is prepended to every candidate. Leading and trailing slashes are trimmed, and the root path `/` is looked up as `index`.

A mock is answered as it is with status 200. To answer with another status the file wraps the response in an envelope read by `MockEnvelope` with Gson: `{ "statusCode": 201, "response": { ... } }` answers 201 with the `response` value as the body, and `{ "statusCode": 204 }` answers with no body. An optional `"headers": { "Location": "/users/4" }` object adds response headers (values may be strings, numbers or booleans; `Content-Type` replaces the default), and an optional `"delayMs": 2000` replaces the interceptor's latency for that file. A file is an envelope only when it is an object that has `statusCode` and no keys other than `statusCode`, `headers`, `delayMs` and `response`; everything else, including a real payload with a `statusCode` field plus other keys, is served untouched. An envelope whose `statusCode` is not an HTTP status, whose `headers` is not an object of valid names and simple values, or whose `delayMs` is not a whole number from 0 up, fails the call with an `IOException` naming the file.

Each candidate is handed to an `InputStreamProvider`, the single extension point that decides where files come from. `DefaultInputStreamProvider` reads from the filesystem; `ClasspathInputStreamProvider` from a class loader (it does not list: a listed classpath variant was dropped before 4.1.0 because an APK has no folder entries to walk); Android consumers pass one backed by `assets.open(path)` (see `MainActivity.assetsProvider`). A provider signals "not found" by returning `null`.

Behaviours that are easy to miss:

- Host, headers and request body are ignored; only method, path and query select the file.
- A provider that also implements `ListableInputStreamProvider` (`providers` package) is asked for `list()` once, on the first request (lazily, a failed `list()` is retried on the next request), and then only for the first candidate that is in that set; if `provide` returns `null` for a listed file the next candidate is tried. Answers, the 404 text and `MissingMockException.triedFiles` are the same as without the list. `DefaultInputStreamProvider` deliberately does not list. The sample's `ListedAssets` walks `AssetManager.list`.
- Without a list, every miss costs one provider call. A request whose exact file exists makes at most six lookups; a request answered by an `{id}` file, or by nothing, makes from a handful to over a hundred (two to six per path variant, with `1 + n + n(n-1)/2` variants for `n` segments). Providers must return `null` quietly and cheaply for a missing file.
- A provider is asked for the method files before the shared ones. A provider with its own fallback (for example a generic `_id.json` for any id) must not mistake the method suffix for a path segment (the built-in `{id}` files make such providers unnecessary); this broke a real app during migration and is called out in the README migration guide.
- The real network is never hit: `chain.proceed` is not called.
- No matching file → 404 with a `text/plain` body listing the exact-path files that were tried and counting the `{id}` ones. This wins over the simulated failure.
- `Builder.failOnMissingMock()` replaces that 404 with a `MissingMockException` (an `IOException`, so asynchronous calls get it in `onFailure`). `Builder.recordCalls()` keeps each request as a `RecordedCall` (method, url, mock file, status and the request body as an okio `ByteString`; status is `null` when the call failed for a missing mock, body is `null` when the request had none or it is duplex, and it is only read when recording is on) in `recordedCalls`, a snapshot guarded by a lock; `clearRecordedCalls()` empties it. Both are off by default and unavailable through the 3.0 constructors.
- `failurePercentage` (0–100) is the chance of answering with `failureCode` (504 by default) and an empty body.
- Every call sleeps a random time in `[minDelay, maxDelay)` (default 500–1500 ms), or exactly the envelope's `delayMs` when it has one; equal bounds give a fixed delay. The envelope is parsed before the sleep, so an invalid one fails without waiting. An interrupt during the sleep throws `InterruptedIOException`.
- A plain mock is returned byte for byte as `application/json` (or the type of its extension); an envelope's `response` is re-serialised by Gson (compact, nulls and number text preserved). The status line carries the reason phrase from `ReasonPhrases` (every IANA-registered code with its RFC 9110 name, plus 418; any other code gets its class name, such as `Client Error`).
- Gson is an `implementation` dependency used only by `MockEnvelope`; files that are not `.json` or do not contain the text `statusCode` are never parsed.
- The configuration is immutable; the only mutable state is the list of recorded calls. Instances are created with the constructors kept from 3.0 or with `OkHttpMockInterceptor.Builder`, which also exposes `failureCode`, `logger` and `noDelay()`.
- Each call logs one line through `MockLogger` (`STDOUT` by default, `NONE` to silence).
- Invalid configuration (percentage outside 0–100, negative or inverted delays, non-error `failureCode`) throws `IllegalArgumentException` at construction.

## Git workflow

Reduced git-flow, without the `git flow` extension:

- `master` holds released versions only. It changes only on a release: merge `develop` → `master` and tag (`vX.Y.Z`).
- `develop` is the integration branch.
- `feature/*` branches are created from `develop` and merged only into `develop`, never into `master`.
- `develop` is the default branch on GitHub, so pull requests target it by default and the pull request template, the issue templates and the Dependabot configuration are read from it.

`CONTRIBUTING.md` explains the same rules for outside contributors, and `CHANGELOG.md` lists the changes of each version; a change in behaviour or public API adds a line there.
