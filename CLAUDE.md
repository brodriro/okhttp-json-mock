# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A small JVM library that mocks HTTP responses for OkHttp/Retrofit: an OkHttp `Interceptor` short-circuits every request and answers with the contents of a local JSON file whose path mirrors the request URL. This repo is the `brodriro` fork of `mirrajabi/okhttp-json-mock`, continuing from 3.0 and openly presented as a fork.

## Modules

- `okhttpjsonmock/` — the library. Plain `java-library`, Java 8 bytecode, published as `io.github.brodriro:okhttp-json-mock`.
- `app/` — Android sample app (Retrofit + RxJava2, AndroidX) that consumes the library via `project(":okhttpjsonmock")`. Its mock data lives in `app/src/main/assets/api/v1/...`.

## Commands

Toolchain: Gradle 9.8.1 (wrapper), AGP 9.4.1, Kotlin DSL build scripts, versions in `gradle/libs.versions.toml`. Needs JDK 17+ (21 is what is installed locally). `app` also needs an Android SDK (`ANDROID_HOME` or `local.properties`) with platform 36.

```sh
./gradlew :okhttpjsonmock:build                # build the library only
./gradlew :app:assembleDebug                   # build the sample app
./gradlew :app:installDebug                    # install the sample on a connected device
./gradlew :okhttpjsonmock:publishToMavenLocal  # jar + sources + javadoc + pom into ~/.m2
```

The library has JUnit 5 tests written in Kotlin under `okhttpjsonmock/src/test/kotlin` (the main sources are still Java). They drive the interceptor through a real `OkHttpClient` with an in-memory `InputStreamProvider`.

```sh
./gradlew :okhttpjsonmock:test                                              # all library tests
./gradlew :okhttpjsonmock:test --tests 'ir.mirrajabi.okhttpjsonmock.OkHttpMockInterceptorTest'
./gradlew build                                                             # compile + tests + Android lint
```

The library version is set in `okhttpjsonmock/build.gradle.kts` and must be kept in sync with the README (dependency snippet and Changelog) on release. Publishing goes through the `com.vanniktech.maven.publish` plugin; signing is only enabled when a signing key is configured.

## How the interceptor resolves a request

All logic is in `OkHttpMockInterceptor.intercept`. The lookup is:

1. `basePath + <encodedPath without leading slash> + "/" + <encodedQuery> + ".json"` — only when there is a query string.
   e.g. `GET /api/v1/users?page=1` → `api/v1/users/page=1.json`; multiple params keep the URL's `&` order: `page=1&categoryId=5.json`.
2. Fallback: `basePath + <encodedPath> + ".json"`, e.g. `/api/v1/users/1` → `api/v1/users/1.json`.

The resulting relative path is handed to an `InputStreamProvider`, the single extension point that decides where files come from. `DefaultInputStreamProvider` reads from the filesystem; Android consumers pass one backed by `getAssets().open(path)` (see `MainActivity.getAndroidProvider`). A provider signals "not found" by returning `null`.

Behaviours that are easy to miss:

- Host, HTTP method, headers and request body are ignored; only path and query select the file.
- The real network is never hit: `chain.proceed` is not called.
- Every call sleeps a random time in `[minDelay, maxDelay)` (default 500–1500 ms). `min == max` causes a modulo-by-zero `ArithmeticException`.
- `failurePercentage` (0–100) is the chance of returning status 504 instead of 200. The body is still the file contents in both cases.
- The file contents are also passed to `Response.Builder.message(...)`. When no file matches, that is `null`, so OkHttp throws `IllegalStateException` instead of returning an empty response.
- Since 3.0 the JSON file is returned verbatim; there is no response wrapper object.

## Git workflow

Reduced git-flow, without the `git flow` extension:

- `master` holds released versions only. It changes only on a release: merge `develop` → `master` and tag (`vX.Y.Z`).
- `develop` is the integration branch.
- `feature/*` branches are created from `develop` and merged only into `develop`, never into `master`.
