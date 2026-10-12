# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A small JVM library that mocks HTTP responses for OkHttp/Retrofit: an OkHttp `Interceptor` short-circuits every request and answers with the contents of a local JSON file whose path mirrors the request URL. This repo is the `brodriro` fork of `mirrajabi/okhttp-json-mock`, continuing from 3.0 and openly presented as a fork.

## Modules

- `okhttp-json-mock/` — the library. Kotlin (`explicitApi`), Java 8 bytecode, published as `io.github.brodriro:okhttp-json-mock`.
- `app/` — Android sample app (Retrofit + RxJava2, AndroidX) that consumes the library via `project(":okhttp-json-mock")`. Its mock data lives in `app/src/main/assets/api/v1/...`.

## Commands

Toolchain: Gradle 9.8.1 (wrapper), AGP 9.4.1, Kotlin DSL build scripts, versions in `gradle/libs.versions.toml`. Needs JDK 17+ (21 is what is installed locally). `app` also needs an Android SDK (`ANDROID_HOME` or `local.properties`) with platform 36.

```sh
./gradlew :okhttp-json-mock:build                # build the library only
./gradlew :app:assembleDebug                   # build the sample app
./gradlew :app:installDebug                    # install the sample on a connected device
./gradlew :okhttp-json-mock:publishToMavenLocal  # jar + sources + javadoc + pom into ~/.m2
```

The library has JUnit 5 tests written in Kotlin under `okhttp-json-mock/src/test/kotlin`. They drive the interceptor through a real `OkHttpClient` with an in-memory `InputStreamProvider`.

```sh
./gradlew :okhttp-json-mock:test                                              # all library tests
./gradlew :okhttp-json-mock:test --tests 'io.github.brodriro.okhttpjsonmock.OkHttpMockInterceptorTest'
./gradlew build                                                             # compile + tests + Android lint
```

The library version is set in `okhttp-json-mock/build.gradle.kts` and must be kept in sync with the README (dependency snippet and Changelog) on release. Publishing goes through the `com.vanniktech.maven.publish` plugin; signing is only enabled when a signing key is configured.

## How the interceptor resolves a request

`OkHttpMockInterceptor.intercept` (`io.github.brodriro.okhttpjsonmock`) asks `MockFileResolver.candidates(method, url)` for the files that can answer the request and uses the first one the provider has. For `GET /api/v1/users?page=1` the order is:

1. `api/v1/users/page=1.get.json` — this method, this query.
2. `api/v1/users.get.json` — this method, any query.
3. `api/v1/users/page=1.json` — any method, this query.
4. `api/v1/users.json` — any method, any query.

The method suffix is the HTTP method in lower case (`.get`, `.post`, `.put`, `.delete`, `.patch`, …). Files without it answer every method, which keeps 3.0 mocks working and is how one URL gets a different mock per method.

With several query parameters the exact string is tried first (`page=1&categoryId=5`) and then the same parameters sorted (`categoryId=5&page=1`), in both the method and the shared group. `basePath` is prepended to every candidate. Leading and trailing slashes are trimmed, and the root path `/` is looked up as `index`.

Each candidate is handed to an `InputStreamProvider`, the single extension point that decides where files come from. `DefaultInputStreamProvider` reads from the filesystem; Android consumers pass one backed by `getAssets().open(path)` (see `MainActivity.getAndroidProvider`). A provider signals "not found" by returning `null`.

Behaviours that are easy to miss:

- Host, headers and request body are ignored; only method, path and query select the file.
- Every miss costs one provider call, so a request can make up to six lookups. Providers must return `null` quietly for a missing file.
- The real network is never hit: `chain.proceed` is not called.
- No matching file → 404 with a `text/plain` body listing the files that were tried. This wins over the simulated failure.
- `failurePercentage` (0–100) is the chance of answering with `failureCode` (504 by default) and an empty body.
- Every call sleeps a random time in `[minDelay, maxDelay)` (default 500–1500 ms); equal bounds give a fixed delay. An interrupt during the sleep throws `InterruptedIOException`.
- The file is returned byte for byte as `application/json`; the status line carries the standard reason phrase.
- Instances are immutable. They are created with the constructors kept from 3.0 or with `OkHttpMockInterceptor.Builder`, which also exposes `failureCode`, `logger` and `noDelay()`.
- Each call logs one line through `MockLogger` (`STDOUT` by default, `NONE` to silence).
- Invalid configuration (percentage outside 0–100, negative or inverted delays, non-error `failureCode`) throws `IllegalArgumentException` at construction.

## Git workflow

Reduced git-flow, without the `git flow` extension:

- `master` holds released versions only. It changes only on a release: merge `develop` → `master` and tag (`vX.Y.Z`).
- `develop` is the integration branch.
- `feature/*` branches are created from `develop` and merged only into `develop`, never into `master`.
