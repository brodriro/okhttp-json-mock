# okhttp-json-mock

[![CI](https://github.com/brodriro/okhttp-json-mock/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/brodriro/okhttp-json-mock/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.brodriro/okhttp-json-mock)](https://central.sonatype.com/artifact/io.github.brodriro/okhttp-json-mock)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

Mock your OkHttp and Retrofit calls with local JSON files. An interceptor answers every request with the
contents of a file whose path mirrors the request URL, so the real network is never used.

```
GET  /api/v1/users/1   →  api/v1/users/1.json
POST /api/v1/users     →  api/v1/users.post.json
GET  /api/v1/users/42  →  api/v1/users/{id}.json
```

> **This is a fork.** It continues [mirrajabi/okhttp-json-mock](https://github.com/mirrajabi/okhttp-json-mock)
> from version 3.0, which has not been updated since 2018. Version 4.0 keeps the same idea and class names,
> migrates the library to Kotlin and adds mocks per HTTP method and per status code.
> Credit for the original library goes to [Mohammad Mirrajabi](https://github.com/mirrajabi) and its contributors.

## Contents

- [Features](#features)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Android setup](#android-setup)
- [Where to put the mock files](#where-to-put-the-mock-files)
- [Paths with ids](#paths-with-ids)
- [One URL, different HTTP methods](#one-url-different-http-methods)
- [The response: status codes, headers and latency per mock](#the-response-status-codes-headers-and-latency-per-mock)
- [Mocks that are not JSON](#mocks-that-are-not-json)
- [Latency](#latency)
- [Simulated failures](#simulated-failures)
- [Logging](#logging)
- [When no mock matches](#when-no-mock-matches)
- [Using it in tests](#using-it-in-tests)
- [Configuration reference](#configuration-reference)
- [Writing an InputStreamProvider](#writing-an-inputstreamprovider)
- [Recipes](#recipes)
- [How it works](#how-it-works)
- [Example](#example)
- [Troubleshooting](#troubleshooting)
- [Migrating from 3.0](#migrating-from-30)

## Features

- **Mocks are plain files.** The URL path is the file path: no code per endpoint, no server to start.
  [More](#where-to-put-the-mock-files)
- **One URL, several HTTP methods.** `users.get.json` and `users.post.json` answer `GET /users` and
  `POST /users`. A file without a method answers all of them. [More](#one-url-different-http-methods)
- **Paths with ids.** `users/{id}.json` answers `/users/1`, `/users/42` and any other id, and a file for
  an exact path still wins. [More](#paths-with-ids)
- **Query strings.** `users?page=2` is answered by `users/page=2.json`, with a fallback to `users.json`
  and parameters in any order. [More](#where-to-put-the-mock-files)
- **Status code, headers and latency per mock**, written inside the mock file:
  `{ "statusCode": 201, "headers": { ... }, "delayMs": 2000, "response": { ... } }`.
  [More](#the-response-status-codes-headers-and-latency-per-mock)
- **Not only JSON:** XML, plain text, images or any other file, served with the `Content-Type` of its
  extension once the extension is listed. [More](#mocks-that-are-not-json)
- **Simulated network:** a random latency on every call, and an optional percentage of calls that
  fail with a configurable status. [Latency](#latency), [failures](#simulated-failures)
- **Clear answers when something is missing:** a 404 that lists the files that were looked for, or an
  exception in tests. [More](#when-no-mock-matches)
- **Test helpers:** fail on a missing mock, and record which calls were made, with which file and
  status. [More](#using-it-in-tests)
- **Any source of files:** Android assets, the filesystem, classpath resources or a map in memory,
  through a one-method interface. [More](#writing-an-inputstreamprovider)
- **One log line per call**, saying which file answered and with which status. [More](#logging)
- Written in Kotlin, usable from Java, on Android or any JVM. Immutable and safe to share between
  threads.

## Installation

The library is on Maven Central:

```kotlin
dependencies {
    implementation("io.github.brodriro:okhttp-json-mock:4.0.0")
}
```

Requirements:

- Java 8 or newer. On Android, API 21 or newer, which is what OkHttp supports.
- OkHttp 4.12 or newer, including 5.x. The library brings OkHttp 4.12 as an `api` dependency; a newer
  version in your build wins as usual.
- In Kotlin projects, Kotlin 1.9 or newer. Java projects need nothing else.
- The library also brings Gson 2.14 (at runtime only, to read wrappers) and the Kotlin standard library.

The API reference of the latest release is at <https://brodriro.github.io/okhttp-json-mock/>.

## Quick start

**1. Write a mock file**, `app/src/main/assets/api/v1/users/1.json`. Its path is the path of the URL
plus `.json`:

```json
{ "id": 1, "name": "John" }
```

**2. Tell the interceptor where the files are.** On Android they usually live in `assets`:

```kotlin
val assets = InputStreamProvider { path ->
    try {
        context.assets.open(path)
    } catch (e: IOException) {
        null // no mock at this path
    }
}
```

**3. Add the interceptor to your client:**

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(OkHttpMockInterceptor.Builder(assets).build())
    .build()

val retrofit = Retrofit.Builder()
    .baseUrl("https://example.com/")
    .client(client)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
```

That is all. A Retrofit call to `GET api/v1/users/1` now returns the file, with status `200`, after a
short simulated delay:

```kotlin
interface UsersService {
    @GET("api/v1/users/{id}")
    suspend fun getUser(@Path("id") id: Int): User
}

val user = retrofit.create(UsersService::class.java).getUser(1) // User(id=1, name=John)
```

The same in Java:

```java
InputStreamProvider assets = path -> {
    try {
        return getAssets().open(path);
    } catch (IOException e) {
        return null; // no mock at this path
    }
};

OkHttpClient client = new OkHttpClient.Builder()
    .addInterceptor(new OkHttpMockInterceptor.Builder(assets).build())
    .build();
```

On a plain JVM project, `OkHttpMockInterceptor.Builder()` without arguments reads the files from the
filesystem, relative to the working directory.

## Android setup

The interceptor answers **every** request of the client it is added to, so add it only where you want
mocks, typically in debug builds:

```kotlin
val builder = OkHttpClient.Builder()
if (BuildConfig.DEBUG) {
    builder.addInterceptor(OkHttpMockInterceptor.Builder(assets).build())
}
```

With AGP 8 or newer, `BuildConfig` has to be enabled with `buildFeatures { buildConfig = true }`.

Tips:

- **Keep the mocks out of the release APK** by putting them in `src/debug/assets` instead of
  `src/main/assets`. Android merges that folder only into debug builds. The mocks for a product flavor
  can go in `src/<flavor>/assets` the same way.
- **Use `addInterceptor`, not `addNetworkInterceptor`.** A network interceptor runs only after OkHttp has
  connected to the server, and since the mock never calls `proceed`, OkHttp fails the call with an
  `IllegalStateException`.
- **The base URL is part of the path.** With `baseUrl("https://example.com/mocks/")`, a call to
  `users` is looked up as `mocks/users.json`. The host is never part of it.
- **Release builds need no keep rules.** The library ships nothing that R8 has to keep; the sample app
  is minified on every build to check it.
- Files and folders named `{id}` are packaged by aapt with the default settings. No
  `ignoreAssetsPattern` is needed.

## Where to put the mock files

The file path is the URL path plus `.json`. The host, the port and the scheme are ignored.

| Request | Mock file |
| --- | --- |
| `GET /api/v1/users/1` | `api/v1/users/1.json` |
| `GET /api/v1/users/1/phoneNumbers` | `api/v1/users/1/phoneNumbers.json` |
| `GET /api/v1/users?page=1` | `api/v1/users/page=1.json` |
| `GET /api/v1/posts/3?page=1&categoryId=5` | `api/v1/posts/3/page=1&categoryId=5.json` |
| `GET /api/v1/users/` | `api/v1/users.json` (a trailing slash is ignored) |
| `GET /` | `index.json` |

A query string becomes a file **inside** a folder named after the path. When there is no file for the
query, the file for the path (`api/v1/users.json`) is used instead, so one file can answer every page
and a few files can answer specific ones.

With several parameters, the file can be named in the order the request uses or with the parameters
sorted alphabetically (`categoryId=5&page=1.json`). The sorted name answers the request whatever order
the client builds the query in.

The path and the query are used as they appear in the URL, percent-encoding included: a search for
`hello world` is looked up as `search/q=hello%20world.json`.

## Paths with ids

Name a file or a folder `{id}`, braces included, to answer any value in that position of the path:

```
api/v1/users/{id}.json               GET /api/v1/users/1, /api/v1/users/42, ...
api/v1/users/{id}/phoneNumbers.json  GET /api/v1/users/7/phoneNumbers
api/v1/users/{id}.delete.json        DELETE /api/v1/users/<any id>
```

- The name is always `{id}`, whatever the parameter is called in your API.
- A file for the exact path wins over `{id}`, so `users/search.json` and `users/1.json` keep answering
  their own URLs. Adding an `{id}` file never changes the answer of a URL that has a file of its own.
- Up to two segments of a path can be `{id}`, as in `orders/{id}/items/{id}.json`. When several `{id}`
  files could answer, a file with one `{id}` wins over a file with two, and among those with the same
  number the one that replaces later segments wins: `users/{id}` before `{id}/1`.
- The base path you configure is never replaced by `{id}`.
- Every `{id}` file returns the same content for every id. The value of the id is not inserted into
  the response.

## One URL, different HTTP methods

Put the HTTP method, in lower case, before the extension:

```
api/v1/users.get.json       GET    /api/v1/users
api/v1/users.post.json      POST   /api/v1/users
api/v1/users/1.put.json     PUT    /api/v1/users/1
api/v1/users/1.patch.json   PATCH  /api/v1/users/1
api/v1/users/1.delete.json  DELETE /api/v1/users/1
```

A file **without** a method answers every method, so mocks written for 3.0 keep working.

### The full lookup order

For `GET /api/v1/users?page=1` the interceptor uses the first of these files that exists:

1. `api/v1/users/page=1.get.json`, this method and this query.
2. `api/v1/users.get.json`, this method and any query.
3. `api/v1/users/page=1.json`, any method and this query.
4. `api/v1/users.json`, any method and any query.

With several query parameters, the sorted name is tried right after the name as sent, in both the
method and the shared group.

Only when none of those exists does it try the same names with `{id}` in place of a path segment: one
segment at a time starting from the last (`api/v1/{id}`, `api/{id}/users`, `{id}/v1/users`), then two
at a time. The `basePath` is prepended to every name.

With [other extensions](#mocks-that-are-not-json) each of these names is tried with every extension, in
the order they were listed, before moving to the next name.

## The response: status codes, headers and latency per mock

By default the file **is** the response body, returned byte for byte with status `200` and
`Content-Type: application/json`. It can be any JSON value, an object or an array:

```json
[
  { "id": 1, "name": "John" },
  { "id": 2, "name": "Jane" }
]
```

To answer with another status code, add headers or change the latency of one mock, wrap the response:

```json
{
  "statusCode": 201,
  "headers": {
    "Location": "/api/v1/users/4",
    "X-Total-Count": 4
  },
  "delayMs": 1200,
  "response": { "id": 4, "name": "Ana" }
}
```

The app receives a `201 Created` with those headers, after 1.2 seconds, and a body that is only what is
inside `response`: `{"id":4,"name":"Ana"}`.

| Key | Required | Meaning |
| --- | --- | --- |
| `statusCode` | yes | HTTP status code, from 100 to 599. |
| `response` | no | The body: an object, an array or any JSON value. No body when missing. |
| `headers` | no | Response headers. Values can be strings, numbers or booleans. `Content-Type` replaces the default `application/json`. |
| `delayMs` | no | Latency of this mock in milliseconds, a whole number from 0 up. Replaces the [delay of the interceptor](#latency) for this file, so one slow endpoint can be simulated, or one made instant. |

Good to know:

- **A wrapper needs `statusCode`.** To change only the latency or the headers of a normal response,
  write `"statusCode": 200`.
- **A file is only read as a wrapper** when it is an object that has `statusCode` and no keys other
  than these four. A real payload such as `{ "statusCode": 404, "message": "Not found" }` has another
  key, so it is returned as it is.
- **`response` is re-serialised** as compact JSON. The values, `null`s and number formats are kept;
  spacing and line breaks are not.
- **The status line carries the standard reason phrase** for the code: `201 Created`,
  `422 Unprocessable Content`, `503 Service Unavailable`. A code without a registered name gets the
  name of its class, such as `Client Error`.
- **An invalid wrapper fails the call** with an `IOException` that names the file: a `statusCode` out
  of range, `headers` that is not an object of valid header names with simple values, or a `delayMs`
  that is not a whole number from 0 up.

A response with no body:

```json
{ "statusCode": 204 }
```

An error with a body:

```json
{
  "statusCode": 422,
  "response": { "error": "email_taken", "message": "That email is already registered" }
}
```

## Mocks that are not JSON

Mock files are `.json` unless more extensions are listed with `extensions(...)`:

```kotlin
OkHttpMockInterceptor.Builder(provider)
    .extensions("json", "xml", "png")
    .build()
```

`GET /api/v1/feed` is then answered by the first of `api/v1/feed.get.json`, `api/v1/feed.get.xml`,
`api/v1/feed.get.png`, `api/v1/feed.json`, `api/v1/feed.xml` and `api/v1/feed.png` that exists. A
more specific name always wins over the order of the extensions, and `{id}` files work with any
extension: `images/{id}.png` answers `/images/1` and `/images/42`.

The file is returned byte for byte, so binary files such as images arrive untouched, with the
`Content-Type` of the extension that matched:

| Extension | `Content-Type` |
| --- | --- |
| `json` | `application/json` |
| `txt` | `text/plain; charset=utf-8` |
| `html`, `htm` | `text/html; charset=utf-8` |
| `csv` | `text/csv; charset=utf-8` |
| `xml` | `application/xml` |
| `svg` | `image/svg+xml` |
| `png` | `image/png` |
| `jpg`, `jpeg` | `image/jpeg` |
| `gif` | `image/gif` |
| `webp` | `image/webp` |
| `pdf` | `application/pdf` |
| any other | `application/octet-stream` |

Good to know:

- **The list replaces the default**, so include `json` to keep finding JSON mocks. A leading dot is
  ignored (`".xml"` is `"xml"`), and an extension is letters and digits only.
- **Only `.json` files are read as [wrappers](#the-response-status-codes-headers-and-latency-per-mock).**
  Any other file is served as it is with status `200`, even if its text looks like a wrapper.
- **Each extension adds one lookup per file name.** Listing three extensions asks the provider for
  three times as many files, so list only the ones in use and keep `provide` cheap for a missing file.

## Latency

Every call waits before it is answered, to imitate a network, so loading states and spinners show up
as they would against a real server.

| Configuration | Wait |
| --- | --- |
| Nothing set | A random time from 500 ms up to 1500 ms. |
| `delay(200, 800)` | A random time from 200 ms up to 800 ms (the upper bound is exclusive). |
| `delay(300, 300)` | Exactly 300 ms. |
| `noDelay()` | No wait. What tests usually want. |
| `"delayMs"` in the mock | Exactly that time, for that file, whatever the interceptor says, also with `noDelay()`. |

A request with no mock waits the interceptor's delay before answering 404. Interrupting the thread
during the wait fails the call with an `InterruptedIOException` and keeps the interrupt flag.

## Simulated failures

```kotlin
OkHttpMockInterceptor.Builder(assets)
    .failurePercentage(10)   // 10% of the calls fail
    .failureCode(503)        // with 503 Service Unavailable; 504 Gateway Timeout by default
    .build()
```

A simulated failure answers with the failure code and an empty body, without the headers of the mock.
It still waits the latency of the mock. A request with no mock answers 404 even when it was going to
fail. `failureCode` must be a 4xx or 5xx status.

## Logging

Each call logs one line, to standard output by default:

```
JsonMockServer: GET /api/v1/users?page=1 -> 200 from api/v1/users/page=1.json
JsonMockServer: POST /api/v1/users -> 201 from api/v1/users.post.json
JsonMockServer: GET /api/v1/users -> 503, simulated failure
JsonMockServer: GET /api/v1/unknown -> 404, no mock found. Tried: api/v1/unknown.get.json, api/v1/unknown.json and 12 more with {id} in place of a path segment
```

File names are written with the `basePath` in front. Send the log somewhere else, or silence it, with
`logger`:

```kotlin
.logger { message -> Log.d("Mocks", message) }   // Android Logcat
.logger { message -> Timber.d(message) }         // Timber
.logger(MockLogger.NONE)                         // no log
```

## When no mock matches

The interceptor answers `404 Not Found` with a plain-text body that lists the files it looked for:

```
No mock file found for GET /api/v1/unknown. Tried: api/v1/unknown.get.json, api/v1/unknown.json and 12 more with {id} in place of a path segment
```

The log line lists the same files. In tests you can make this an exception instead: see
[Using it in tests](#using-it-in-tests).

## Using it in tests

The interceptor works in plain JVM unit tests: no emulator, no server. Three options make it fit for
tests:

```kotlin
val interceptor = OkHttpMockInterceptor.Builder(provider)
    .noDelay()             // otherwise every call waits 500 to 1500 ms
    .failOnMissingMock()   // a request without a mock throws instead of answering 404
    .recordCalls()         // keep the requests that were received
    .logger(MockLogger.NONE)
    .build()
```

A complete test, with the mocks in a map:

```kotlin
class UserRepositoryTest {
    private val mocks = mapOf(
        "api/v1/users/1.json" to """{ "id": 1, "name": "John" }""",
        "api/v1/users.post.json" to """{ "statusCode": 201, "response": { "id": 4, "name": "Ana" } }""",
    )
    private val interceptor = OkHttpMockInterceptor.Builder { path -> mocks[path]?.byteInputStream() }
        .noDelay()
        .failOnMissingMock()
        .recordCalls()
        .logger(MockLogger.NONE)
        .build()
    private val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
    private val repository = UserRepository(client)

    @Test
    fun `creating a user posts it`() {
        val user = repository.create(name = "Ana")

        assertEquals(4, user.id)
        val call = interceptor.recordedCalls.single()
        assertEquals("POST", call.method)
        assertEquals("/api/v1/users", call.url.encodedPath)
        assertEquals(201, call.statusCode)
        assertEquals("api/v1/users.post.json", call.mockFile)
    }
}
```

**`failOnMissingMock()`.** A request that has no mock file fails with a `MissingMockException`. It is an
`IOException`, so OkHttp delivers it like any failed call (in `onFailure` for asynchronous calls), and
its message names the request and the files that were tried. A forgotten mock shows up as what it is,
not as a server error handled somewhere in your code. It also has:

| Property | Content |
| --- | --- |
| `method` | HTTP method of the request. |
| `url` | `HttpUrl` of the request. |
| `triedFiles` | Every file that was looked up, in order. |

**`recordCalls()`.** `recordedCalls` returns the requests received so far, oldest first, as a
snapshot that later calls do not change. `clearRecordedCalls()` empties it, for example between tests.
Recording is off by default because the list grows for as long as the interceptor is used. Each
`RecordedCall` has:

| Property | Content |
| --- | --- |
| `method` | HTTP method of the request. |
| `url` | `HttpUrl` of the request, with its query. |
| `mockFile` | The file that answered, with the `basePath` in front, or `null` when none matched. |
| `statusCode` | The status of the answer, a simulated failure's included, or `null` when the call failed with `MissingMockException`. |

The request body and headers are not recorded.

**Reusing the app's mocks in unit tests.** Gradle runs unit tests from the module folder, so the
filesystem provider can read the same files the app uses:

```kotlin
OkHttpMockInterceptor.Builder(DefaultInputStreamProvider())
    .basePath("src/main/assets/")   // or src/debug/assets/
    .noDelay()
    .build()
```

Mocks only for tests can go in `src/test/resources` and be read from the classpath: see
[Writing an InputStreamProvider](#writing-an-inputstreamprovider).

## Configuration reference

### `OkHttpMockInterceptor.Builder`

| Method | Default | Notes |
| --- | --- | --- |
| `Builder(provider)` | | Where the mock files are read from. |
| `Builder()` | | Reads from the filesystem with `DefaultInputStreamProvider`. |
| `basePath(String)` | `""` | Prefix added to every file name, for example `"mocks/"`. Include the trailing slash. |
| `delay(min, max)` | `500, 1500` | Latency in ms, from `min` up to `max` exclusive. Equal bounds give a fixed latency. Both from 0 up, `max` not lower than `min`. |
| `noDelay()` | | Same as `delay(0, 0)`. |
| `failurePercentage(Int)` | `0` | From 0 to 100. |
| `failureCode(Int)` | `504` | A 4xx or 5xx status. |
| `logger(MockLogger)` | `MockLogger.STDOUT` | `MockLogger.NONE` silences it. |
| `extensions(vararg String)` | `"json"` | Extensions of mock files, in the order they are tried. Replaces the default. See [Mocks that are not JSON](#mocks-that-are-not-json). |
| `failOnMissingMock()` | off | Throw `MissingMockException` instead of answering 404. |
| `recordCalls()` | off | Keep the requests in `recordedCalls`. |
| `build()` | | Validates the configuration and throws `IllegalArgumentException` if a value is out of range. |

The interceptor exposes its configuration as read-only properties: `basePath`, `failurePercentage`,
`failureCode`, `minDelayMilliseconds`, `maxDelayMilliseconds` and `extensions`, plus `recordedCalls` and
`clearRecordedCalls()` (see [Using it in tests](#using-it-in-tests)).

### Constructors kept from 3.0

They keep existing code working. New code is clearer with the builder, which is also the only way to
set `failureCode`, `logger`, `extensions`, `failOnMissingMock()` and `recordCalls()`.

| Constructor | Equivalent builder |
| --- | --- |
| `OkHttpMockInterceptor(failurePercentage)` | `Builder().failurePercentage(p)` |
| `OkHttpMockInterceptor(provider, failurePercentage)` | `Builder(provider).failurePercentage(p)` |
| `OkHttpMockInterceptor(provider, failurePercentage, minDelay, maxDelay)` | `... .delay(minDelay, maxDelay)` |
| `OkHttpMockInterceptor(provider, failurePercentage, basePath, minDelay, maxDelay)` | `... .basePath(basePath).delay(minDelay, maxDelay)` |

## Writing an InputStreamProvider

`InputStreamProvider` is the one place that decides where mock files come from. It has a single
method, `provide(path)`, that returns the file as an `InputStream`, or `null` when there is no file at
that path. The interceptor closes the stream.

The contract:

- **Return `null` for a missing file, quickly and without logging.** The interceptor asks for several
  names per request and most of them do not exist: up to six for a request whose exact file exists, and
  from a handful to over a hundred, depending on the number of path segments and query parameters, for
  one answered by an `{id}` file or by nothing. Each extension listed with `extensions(...)` multiplies
  those numbers.
- **Open exactly the file you are asked for.** Answering an unknown name with some other file breaks
  the order in which mocks are chosen, because the provider is asked for the method files before the
  shared ones. `{id}` files cover the usual reason for doing it.
- **Be safe to call from several threads.** OkHttp runs calls in parallel.

Android assets:

```kotlin
val assets = InputStreamProvider { path ->
    try {
        context.assets.open(path)
    } catch (e: IOException) {
        null
    }
}
```

Classpath resources, for example `src/test/resources`. `getResourceAsStream` already returns `null` for
a missing file:

```kotlin
val resources = InputStreamProvider { path -> javaClass.classLoader.getResourceAsStream(path) }
```

Files in memory:

```kotlin
val mocks = mapOf("api/v1/users/1.json" to """{ "id": 1 }""")
val inMemory = InputStreamProvider { path -> mocks[path]?.byteInputStream() }
```

The filesystem: `DefaultInputStreamProvider()`, which resolves paths against the working directory.
Combine it with `basePath` to point at a folder.

## Recipes

**A paginated list.** One file per page, and a fallback for any other page:

```
api/v1/users/page=1.json    first page
api/v1/users/page=2.json    second page
api/v1/users.json           any other page, for example an empty list: []
```

**Create, read, update and delete on one resource:**

```
api/v1/users.get.json          GET     /users       the list
api/v1/users.post.json         POST    /users       { "statusCode": 201, "headers": { "Location": "/api/v1/users/4" }, "response": { "id": 4 } }
api/v1/users/{id}.get.json     GET     /users/<id>  one user
api/v1/users/{id}.put.json     PUT     /users/<id>  the updated user
api/v1/users/{id}.delete.json  DELETE  /users/<id>  { "statusCode": 204 }
```

**One user that does not exist,** while every other id answers:

```
api/v1/users/{id}.json   any id
api/v1/users/9.json      { "statusCode": 404, "response": { "error": "not_found" } }
```

**A slow endpoint,** to check a loading state or a timeout:

```json
{ "statusCode": 200, "delayMs": 8000, "response": { "report": "..." } }
```

The wait happens inside the interceptor, not on a socket, so `readTimeout` and `connectTimeout` never
cut it short. A `callTimeout` shorter than `delayMs` does fail the call with an
`InterruptedIOException`, but only once the whole wait is over. To test how your code handles a
timeout, answer with `"statusCode": 504` or use `failurePercentage(100)`.

**An unreliable network:** `failurePercentage(20).failureCode(503)` and `delay(1000, 4000)`.

**A different mock for an error and a success of the same call** in one test run: give each case its
own interceptor, or its own provider map, since the file for a URL is always the same.

## How it works

1. OkHttp hands the request to the interceptor, which never calls `chain.proceed`: the request never
   reaches the network, the cache or other interceptors added after it.
2. The method, path and query of the request are turned into the list of candidate files described
   in [the full lookup order](#the-full-lookup-order). Host, headers and body play no part.
3. The provider is asked for each candidate in turn, and the first one it has is the mock.
4. If the mock is a `.json` wrapper, its status, headers, latency and body are read from it.
5. The interceptor waits the latency, decides whether the call is a simulated failure, logs one line,
   records the call if asked to, and builds the response with protocol `HTTP/1.1`.

The configuration is immutable and one interceptor can be shared by any number of clients and threads.
The only state that changes is the list of recorded calls, which is guarded by a lock.

## Example

The [sample app](app) is a catalog of every feature of the library, written with Jetpack Compose. Each
screen explains one feature and sends real requests through an `OkHttpMockInterceptor` that reads the
mock files in [`app/src/main/assets`](app/src/main/assets), showing the status, the headers, the file
that answered and the body, with images decoded and PDFs rendered:

| Screen | What it shows |
| --- | --- |
| Paths and queries | Query files, the fallback to the path file, parameters in any order, nested paths |
| One URL, many methods | `GET`, `PUT`, `DELETE` and `POST` on the same URL, each with its own file |
| Paths with ids | `users/{id}.json` for any id, and the exact file winning over it |
| Status, headers and latency | Wrapped mocks: 201 with `Location`, 204, 404, 422 and a slow mock with `delayMs` |
| Not only JSON | `extensions(...)` with an image, PDF, SVG, XML, HTML, CSV, text and an unknown extension |
| When no mock matches | The 404 that lists the files tried, and `failOnMissingMock()` |
| Simulated network | Latency, failure percentage and status, strict mode, and ten calls at once |
| Recorded calls and log | `recordCalls()` and the `MockLogger` lines of the session |
| With Retrofit | A typed `UsersService` answered by the same files |

Run it with `./gradlew :app:installDebug`. Its instrumented tests (`app/src/androidTest`) check every
feature on a device, one test per extension included, and the library's `SampleMocksTest` checks the
same files on the JVM.

## Troubleshooting

**A request answers 404.** The response body and the log line say which files were looked for:

```
GET /api/v1/users/7 -> 404, no mock found. Tried: api/v1/users/7.get.json, api/v1/users/7.json and 20 more with {id} in place of a path segment
```

Compare those names with the files you have. The usual causes:

- The file is not where the URL says. The path of the file is the path of the URL, including any
  prefix such as `api/v1`, plus the `basePath` you configured.
- The base URL of the client adds a segment you did not count on. With a base URL of
  `https://example.com/mocks/`, a call to `users` is looked up as `mocks/users.json`.
- The file has a method suffix for another method: `users.post.json` does not answer a `GET`.
- The query has percent-encoded characters, which stay encoded in the file name.
- On Android the file is outside `src/main/assets`, or in the `assets` of a build type that is not
  the one you are running.

**The wrong mock answers.** The log line names the file that was used:

```
GET /api/v1/users/search -> 200 from api/v1/users/{id}.json
```

- An `{id}` file answers every path that has no file of its own. Add a file for the exact path,
  here `users/search.json`, and it takes precedence.
- For the same path, a file with a method suffix wins over one without it, even over a file for the
  query; within each of those groups, a file for the query wins over the file for the path. A file
  for the exact path wins over any `{id}` file. See [the full lookup order](#the-full-lookup-order).
- If you wrote your own `InputStreamProvider`, check that it opens exactly the file it is asked for
  and returns `null` for anything else. A provider that falls back to another file changes which
  mock is chosen.

**The body arrives wrapped, or the status is always 200.** A file is only read as a wrapper when it
is an object with `statusCode` and no keys other than `statusCode`, `headers`, `delayMs` and
`response`. With any other key, such as `{ "statusCode": 404, "message": "..." }`, the file is the
body itself.

**A call fails with an `IOException` that names a mock file.** The wrapper in that file has a
`statusCode` that is not a number from 100 to 599, `headers` that is not an object of valid header
names with simple values, or a `delayMs` that is not a whole number from 0 up.

**Calls are slow.** Every call waits between 500 and 1500 ms by default to imitate a network. Use
`delay(min, max)` to change it or `noDelay()` to remove it. A mock with `delayMs` waits that long
whatever the interceptor says.

**A test passes although a mock is missing.** By default a missing mock is a 404, which your code
may handle like any server error. Build the interceptor with `failOnMissingMock()` in tests.

**The app crashes with a `NullPointerException` on a field declared non-null.** Gson leaves the fields
that are missing from the mock `null` (or `0`/`false` for primitives), even when Kotlin declares them
non-null, unless every property of the class has a default value. The crash
happens later, where the field is used. Check that the mock has every field of the class it is read
into; a unit test that reads the app's mocks, as in [Using it in tests](#using-it-in-tests), catches it
before the app runs.

**Requests reach the real server.** The interceptor was added to another `OkHttpClient` than the one
Retrofit uses, or only in a build type you are not running. (Added with `addNetworkInterceptor`
instead, every call fails with `IllegalStateException: network interceptor … must call proceed() exactly
once`.)

## Migrating from 3.0

1. Change the dependency to `io.github.brodriro:okhttp-json-mock:4.0.0`, which is on Maven Central.
   If the JitPack repository was only there for this library, remove it.
2. Change the imports from `ir.mirrajabi.okhttpjsonmock` to `io.github.brodriro.okhttpjsonmock`.
3. If you called the setters (`setBasePath`, `setFailurePercentage`, ...), use `OkHttpMockInterceptor.Builder`
   instead. Instances are now immutable.
4. In Kotlin, a provider written as `object : InputStreamProvider` must declare the parameter as
   non-null: `override fun provide(path: String): InputStream?`. It can also become a lambda:
   `InputStreamProvider { path -> ... }`.

The constructors of 3.0, such as `new OkHttpMockInterceptor(provider, 5)`, and your existing mock files
keep working, except files shaped like a wrapper (see below). The default delay is still 500 to 1500 ms per call.

If your provider does more than open the file it is asked for, check it. For each request it is now
asked for the method files first (`users/1.get.json`) and then for the shared ones (`users/1.json`).
A provider that answers an unknown name with a fallback file, for example a generic `users/_id.json`,
has to tell the method suffix apart from the last path segment, or it will answer the method file with
the wrong fallback.
If the fallback only exists to answer any id, drop it and name the file `{id}.json` instead (see
[Paths with ids](#paths-with-ids)).

Behaviour that changed:

- A request without a mock answers `404` instead of throwing `IllegalStateException`. If you relied
  on the exception, build the interceptor with `failOnMissingMock()`.
- A simulated failure has an empty body instead of the contents of the mock.
- A mock file that is an object with `statusCode` and no keys other than `headers`, `delayMs` and
  `response` is now read as a wrapper. Rename such a key, or wrap the file, if it was meant as a body.
- `failurePercentage` outside 0–100, a negative minimum delay and a minimum delay above the maximum
  now throw `IllegalArgumentException` when the interceptor is created.
- The status line carries a standard reason phrase instead of the mock's contents, responses are
  HTTP/1.1, and the log line has a new format.
- `ResourcesHelper` was removed.
- OkHttp 4.12 or newer is required, and Kotlin projects need Kotlin 1.9 or newer.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## Contributing

Contributions are welcome. Branch from `develop`, run `./gradlew build` and open a pull request
against `develop`. [CONTRIBUTING.md](CONTRIBUTING.md) explains the setup, the project layout and what
the build checks.

## License

[Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for the attribution to the original project.
