# okhttp-json-mock

[![CI](https://github.com/brodriro/okhttp-json-mock/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/brodriro/okhttp-json-mock/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.brodriro/okhttp-json-mock)](https://central.sonatype.com/artifact/io.github.brodriro/okhttp-json-mock)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

Mock your OkHttp and Retrofit calls with local JSON files. An interceptor answers every request with the
contents of a file whose path mirrors the request URL, so the real network is never used.

> **This is a fork.** It continues [mirrajabi/okhttp-json-mock](https://github.com/mirrajabi/okhttp-json-mock)
> from version 3.0, which has not been updated since 2018. Version 4.0 keeps the same idea and class names,
> migrates the library to Kotlin and adds mocks per HTTP method and per status code.
> Credit for the original library goes to [Mohammad Mirrajabi](https://github.com/mirrajabi) and its contributors.

## What is new in 4.0

- **One URL, several HTTP methods.** `users.get.json` and `users.post.json` answer `GET /users` and `POST /users`.
- **Status code and headers per mock**, written inside the mock file itself.
- **Paths with ids.** `users/{id}.json` answers `/users/1`, `/users/42` and any other id.
- **404 instead of a crash** when no mock matches, with the list of files that were tried.
- **Options for tests:** fail on a missing mock and check which calls were made.
- Written in Kotlin, still usable from Java. Works on any JVM project, not only Android.

## Installation

The library is on Maven Central:

```kotlin
dependencies {
    implementation("io.github.brodriro:okhttp-json-mock:4.0.0")
}
```

Requirements:

- Java 8 or newer. On Android, API 21 or newer, which is what OkHttp supports.
- OkHttp 4.12 or newer, including 5.x.
- In Kotlin projects, Kotlin 1.9 or newer. Java projects need nothing else.

The API reference of the latest release is at <https://brodriro.github.io/okhttp-json-mock/>.

## Quick start

Tell the interceptor where your mock files are, then add it to your client.

On Android, mocks usually live in the `assets` folder:

```kotlin
val assets = InputStreamProvider { path ->
    try {
        context.assets.open(path)
    } catch (e: IOException) {
        null // no mock at this path
    }
}

val client = OkHttpClient.Builder()
    .addInterceptor(OkHttpMockInterceptor.Builder(assets).build())
    .build()

val retrofit = Retrofit.Builder()
    .baseUrl("http://example.com")
    .client(client)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
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

The interceptor answers **every** request of the client it is added to, so add it only where you want
mocks, typically in debug builds:

```kotlin
if (BuildConfig.DEBUG) builder.addInterceptor(OkHttpMockInterceptor.Builder(assets).build())
```

On a plain JVM project, `OkHttpMockInterceptor.Builder()` without arguments reads the files from the
filesystem, relative to the working directory.

The provider must return `null` when the file does not exist. The interceptor asks for several files per
request and most of them will not be there.

## Where to put the mock files

The file path is the URL path plus `.json`. The host is ignored.

| Request | Mock file |
| --- | --- |
| `GET /api/v1/users/1` | `api/v1/users/1.json` |
| `GET /api/v1/users/1/phoneNumbers` | `api/v1/users/1/phoneNumbers.json` |
| `GET /api/v1/users?page=1` | `api/v1/users/page=1.json` |
| `GET /api/v1/posts/3?page=1&categoryId=5` | `api/v1/posts/3/page=1&categoryId=5.json` |
| `GET /` | `index.json` |

A query string becomes a file **inside** a folder named after the path. When there is no file for the
query, the file for the path (`api/v1/users.json`) is used instead.

With several parameters, the file can be named in the order the request uses or with the parameters
sorted alphabetically (`categoryId=5&page=1.json`). The sorted name answers the request whatever order
the client builds the query in.

## Paths with ids

Name a file or a folder `{id}`, braces included, to answer any value in that position of the path:

```
api/v1/users/{id}.json               GET /api/v1/users/1, /api/v1/users/42, ...
api/v1/users/{id}/phoneNumbers.json  GET /api/v1/users/7/phoneNumbers
api/v1/users/{id}.delete.json        DELETE /api/v1/users/<any id>
```

The name is always `{id}`, whatever the parameter is called in your API. A file for the exact path
wins over `{id}`, so `users/search.json` and `users/1.json` keep answering their own URLs. Up to two
segments of a path can be `{id}`, as in `orders/{id}/items/{id}.json`.

On Android these files go in `assets` like any other mock. No build configuration is needed.

## One URL, different HTTP methods

Put the HTTP method, in lower case, before the extension:

```
api/v1/users.get.json       GET    /api/v1/users
api/v1/users.post.json      POST   /api/v1/users
api/v1/users/1.put.json     PUT    /api/v1/users/1
api/v1/users/1.delete.json  DELETE /api/v1/users/1
```

A file **without** a method answers every method, so mocks written for 3.0 keep working.

For `GET /api/v1/users?page=1` the interceptor uses the first of these files that exists:

1. `api/v1/users/page=1.get.json`
2. `api/v1/users.get.json`
3. `api/v1/users/page=1.json`
4. `api/v1/users.json`

Only when none of them exists does it try the same four names with `{id}` in place of a path segment.

## The response: objects, arrays, status codes and headers

By default the file **is** the response body, returned as it is with status `200`. It can be a JSON
object or a JSON array:

```json
{ "id": 1, "name": "John" }
```

```json
[
  { "id": 1, "name": "John" },
  { "id": 2, "name": "Jane" }
]
```

To answer with another status code, wrap the response:

```json
{
  "statusCode": 201,
  "response": { "id": 4, "name": "Ana" }
}
```

The app receives a `201` whose body is only what is inside `response`. `response` can be an object or
an array, and can be left out for a response with no body:

```json
{ "statusCode": 204 }
```

`headers` is optional and adds response headers:

```json
{
  "statusCode": 201,
  "headers": {
    "Location": "/api/v1/users/4",
    "X-Total-Count": 4
  },
  "response": { "id": 4, "name": "Ana" }
}
```

| Key | Required | Meaning |
| --- | --- | --- |
| `statusCode` | yes | HTTP status code, from 100 to 599. |
| `response` | no | The body: an object, an array or any JSON value. No body when missing. |
| `headers` | no | Response headers. Values can be strings, numbers or booleans. `Content-Type` replaces the default `application/json`. |

A file is only read as a wrapper when it has `statusCode` and no keys other than these three. A real
payload such as `{ "statusCode": 404, "message": "Not found" }` has another key, so it is returned as
it is.

## Options

```kotlin
val interceptor = OkHttpMockInterceptor.Builder(provider)
    .basePath("mocks/")           // prefix for every file path
    .delay(200, 800)              // simulated latency in milliseconds; default 500 to 1500
    .failurePercentage(10)        // chance, 0 to 100, of a simulated failure; default 0
    .failureCode(503)             // status of a simulated failure; default 504
    .logger(MockLogger.NONE)      // default MockLogger.STDOUT prints one line per call
    .build()
```

`noDelay()` removes the latency, which is useful in tests.

A simulated failure answers with the failure code and an empty body, whatever the mock says.

Each call logs one line:

```
JsonMockServer: POST /api/v1/users -> 201 from api/v1/users.post.json
```

## When no mock matches

The interceptor answers `404` with a plain-text body that lists the files it looked for:

```
No mock file found for GET /api/v1/unknown. Tried: api/v1/unknown.get.json, api/v1/unknown.json and 12 more with {id} in place of a path segment
```

The same text goes to the log. In tests you can make this an exception instead: see
[Using it in tests](#using-it-in-tests).

## Using it in tests

Two options make the interceptor stricter, which is what a test usually wants:

```kotlin
val interceptor = OkHttpMockInterceptor.Builder(provider)
    .noDelay()
    .failOnMissingMock()   // a request without a mock throws instead of answering 404
    .recordCalls()         // keep the requests that were received
    .build()
```

With `failOnMissingMock()`, a request that has no mock file fails with a `MissingMockException`. It
is an `IOException` whose message names the request and the files that were tried, so a forgotten
mock shows up as what it is and not as a server error handled somewhere in your code.

With `recordCalls()`, the interceptor keeps what it received:

```kotlin
val created = interceptor.recordedCalls.single { it.method == "POST" }
assertEquals("/api/v1/users", created.url.encodedPath)
assertEquals(201, created.statusCode)
assertEquals("api/v1/users.post.json", created.mockFile)
```

`clearRecordedCalls()` empties the list between tests. Recording is off by default because the list
grows for as long as the interceptor is used.

## Example

The [sample app](app) reads its mocks from [`app/src/main/assets`](app/src/main/assets):

```
api/v1/
├── users.post.json            POST /api/v1/users         201 + Location header
└── users/
    ├── page=1.json            GET  /api/v1/users?page=1  plain array
    ├── page=2.get.json        GET  /api/v1/users?page=2  wrapped empty array + header
    ├── 1.json                 GET    /api/v1/users/1     plain object
    ├── 1.put.json             PUT    /api/v1/users/1     plain object
    ├── 1.delete.json          DELETE /api/v1/users/1     204, no body
    ├── 9.get.json             GET    /api/v1/users/9     404 with an error body
    ├── {id}.json              GET    /api/v1/users/<any other id>
    ├── 2.json
    ├── 3.json
    └── 1/
        └── phoneNumbers.json  GET /api/v1/users/1/phoneNumbers
```

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
- On Android the file is outside `src/main/assets`, or in the `assets` of a build type that is not
  the one you are running.

**The wrong mock answers.** The log line names the file that was used:

```
GET /api/v1/users/search -> 200 from api/v1/users/{id}.json
```

- An `{id}` file answers every path that has no file of its own. Add a file for the exact path,
  here `users/search.json`, and it takes precedence.
- A file with a method suffix wins over one without it, and a file for a query wins over the file
  for the path. See [the order](#one-url-different-http-methods).
- If you wrote your own `InputStreamProvider`, check that it opens exactly the file it is asked for
  and returns `null` for anything else. A provider that falls back to another file changes which
  mock is chosen.

**The body arrives wrapped, or the status is always 200.** A file is only read as a wrapper when it
is an object with `statusCode` and no keys other than `statusCode`, `headers` and `response`. With
any other key, such as `{ "statusCode": 404, "message": "..." }`, the file is the body itself.

**A call fails with an `IOException` that names a mock file.** The wrapper in that file has a
`statusCode` that is not a number from 100 to 599, or `headers` that is not an object of simple
values.

**Calls are slow.** Every call waits between 500 and 1500 ms by default to imitate a network. Use
`delay(min, max)` to change it or `noDelay()` to remove it.

**A test passes although a mock is missing.** By default a missing mock is a 404, which your code
may handle like any server error. Build the interceptor with `failOnMissingMock()` in tests.

## Migrating from 3.0

1. Change the dependency to `io.github.brodriro:okhttp-json-mock:4.0.0`.
2. Change the imports from `ir.mirrajabi.okhttpjsonmock` to `io.github.brodriro.okhttpjsonmock`.
3. If you called the setters (`setBasePath`, `setFailurePercentage`, ...), use `OkHttpMockInterceptor.Builder`
   instead. Instances are now immutable.
4. In Kotlin, a provider written as `object : InputStreamProvider` must declare the parameter as
   non-null: `override fun provide(path: String): InputStream?`. It can also become a lambda:
   `InputStreamProvider { path -> ... }`.

The constructors of 3.0, such as `new OkHttpMockInterceptor(provider, 5)`, and your existing mock files
keep working.

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
- `ResourcesHelper` was removed.
- OkHttp 4.12 or newer is required, and Kotlin projects need Kotlin 1.9 or newer.

## Changelog

4.0.0 - `Kotlin, mocks per HTTP method, status code and headers per mock, {id} paths, 404 for missing mocks, Builder, options for tests`

3.0 - `Removed wrapper for mocked responses`

2.0 - `The library no longer depends on android classes`

1.1.1 - `Fixes file name lowercase issue`

1.1 - `Adds delay customization option.`

## Contributing

Contributions are welcome. Branch from `develop` and open a pull request against `develop`.

Run `./gradlew build` before opening it: it runs the tests, ktlint, Android lint, the public API
check and the coverage check, the same checks as the CI. `./gradlew ktlintFormat` fixes most style problems. If you change
the public API on purpose, run `./gradlew :okhttp-json-mock:updateKotlinAbi` and commit the result.

## License

[Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for the attribution to the original project.
