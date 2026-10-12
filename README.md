# okhttp-json-mock

Mock your OkHttp and Retrofit calls with local JSON files. An interceptor answers every request with the
contents of a file whose path mirrors the request URL, so the real network is never used.

> **This is a fork.** It continues [mirrajabi/okhttp-json-mock](https://github.com/mirrajabi/okhttp-json-mock)
> from version 3.0, which has not been updated since 2018. Version 4.0 keeps the same idea and class names,
> migrates the library to Kotlin and adds mocks per HTTP method and per status code.
> Credit for the original library goes to [Mohammad Mirrajabi](https://github.com/mirrajabi) and its contributors.

## What is new in 4.0

- **One URL, several HTTP methods.** `users.get.json` and `users.post.json` answer `GET /users` and `POST /users`.
- **Status code and headers per mock**, written inside the mock file itself.
- **404 instead of a crash** when no mock matches, with the list of files that were tried.
- Written in Kotlin, still usable from Java. Works on any JVM project, not only Android.

## Installation

> 4.0.0 is not released yet. It will be published to Maven Central with the coordinates below.
> Until then, the latest released version is 3.0 from the original repository.

```kotlin
dependencies {
    implementation("io.github.brodriro:okhttp-json-mock:4.0.0")
}
```

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
No mock file found for GET /api/v1/unknown. Tried: api/v1/unknown.get.json, api/v1/unknown.json
```

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
    ├── 2.json
    ├── 3.json
    └── 1/
        └── phoneNumbers.json  GET /api/v1/users/1/phoneNumbers
```

## Migrating from 3.0

1. Change the dependency to `io.github.brodriro:okhttp-json-mock:4.0.0`.
2. Change the imports from `ir.mirrajabi.okhttpjsonmock` to `io.github.brodriro.okhttpjsonmock`.
3. If you called the setters (`setBasePath`, `setFailurePercentage`, ...), use `OkHttpMockInterceptor.Builder`
   instead. Instances are now immutable.

The constructors of 3.0, such as `new OkHttpMockInterceptor(provider, 5)`, and your existing mock files
keep working.

Behaviour that changed:

- A request without a mock answers `404` instead of throwing `IllegalStateException`.
- A simulated failure has an empty body instead of the contents of the mock.
- `ResourcesHelper` was removed.
- OkHttp 4.12 is required.

## Changelog

4.0.0 (unreleased) - `Kotlin, mocks per HTTP method, status code and headers per mock, 404 for missing mocks, Builder`

3.0 - `Removed wrapper for mocked responses`

2.0 - `The library no longer depends on android classes`

1.1.1 - `Fixes file name lowercase issue`

1.1 - `Adds delay customization option.`

## Contributing

Contributions are welcome. Branch from `develop` and open a pull request against `develop`.

## License

[Apache License 2.0](LICENSE).
