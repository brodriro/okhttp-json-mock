# Changelog

All notable changes to this library are listed here. Versions follow [semantic versioning](https://semver.org).

Versions up to 3.0 were released by the original project,
[mirrajabi/okhttp-json-mock](https://github.com/mirrajabi/okhttp-json-mock). From 4.0.0 on they are
released by this fork as `io.github.brodriro:okhttp-json-mock`.

## 4.1.0 (unreleased)

### Added

- Mocks that are not JSON: `Builder.extensions("json", "xml", "png")` lists the extensions of mock
  files, tried in that order for each file name, and the response gets the `Content-Type` of the
  extension that matched. Only `.json` files are read as wrappers. The configured list can be read back
  as `extensions`. Without it only `.json` files are looked up, as before.

## 4.0.0

First release of the fork. See [Migrating from 3.0](README.md#migrating-from-30).

### Added

- Mocks per HTTP method: `users.get.json` and `users.post.json` answer `GET /users` and `POST /users`.
  A file without a method answers every method.
- Status code and headers per mock, written in the mock file:
  `{ "statusCode": 201, "headers": { ... }, "response": { ... } }`. Header values may be strings,
  numbers or booleans, and `Content-Type` replaces the default.
- An invalid wrapper (status out of range, bad headers, bad `delayMs`) fails the call with an
  `IOException` that names the file.
- Latency per mock with `delayMs` in the same wrapper, which replaces the delay of the interceptor for
  that file.
- `{id}` files and folders that answer any value of a path segment, such as `users/{id}.json`.
- `OkHttpMockInterceptor.Builder`, with `basePath`, `delay`, `noDelay`, `failurePercentage`,
  `failureCode` and `logger`.
- `MockLogger`, to send the one-line log of each call somewhere else or to silence it.
- Options for tests: `failOnMissingMock()`, which throws `MissingMockException`, and `recordCalls()`,
  which keeps the requests received in `recordedCalls` as `RecordedCall`s (method, URL, mock file,
  status); `clearRecordedCalls()` empties it.
- The configuration can be read back from the interceptor, including the new `failureCode`.
- A query with several parameters also matches a file named with the parameters sorted.
- The root path `/` is looked up as `index`, so `index.json` (or `index.get.json`) answers it.

### Changed

- The library is written in Kotlin. It is still usable from Java and still targets Java 8.
- Coordinates are `io.github.brodriro:okhttp-json-mock` on Maven Central, and the package is
  `io.github.brodriro.okhttpjsonmock`.
- A request without a mock answers 404 with the list of files that were tried, instead of throwing
  `IllegalStateException`.
- A simulated failure answers with an empty body instead of the contents of the mock.
- Each call logs one line with the method, the path and query, the status and the mock file.
- Interceptors are immutable. The setters were removed in favour of the builder.
- `InputStreamProvider` is a functional interface whose `path` is never null, and it is asked for
  more file names per request.
- OkHttp 4.12 or newer is required, including 5.x. Kotlin projects need Kotlin 1.9 or newer.

### Fixed

- Equal minimum and maximum delays no longer fail with a division by zero, and inverted delays are
  rejected when the interceptor is created.
- The status line carries the standard reason phrase of every registered status code (RFC 9110 names,
  plus 418), or the name of its class for any other code, and responses are HTTP/1.1.
- A failure percentage outside 0 to 100 is rejected.
- An interrupt during the simulated delay fails the call with `InterruptedIOException` and keeps the
  thread's interrupted flag.
- The mock is returned byte for byte, so non-ASCII text is no longer altered.
- A trailing slash in the URL is ignored.
- The default provider no longer prints a stack trace for every file that does not exist.

### Removed

- `ResourcesHelper`.

## 3.0

- Removed the wrapper for mocked responses.

## 2.0

- The library no longer depends on Android classes.

## 1.1.1

- Fixed the file name being lower-cased.

## 1.1

- Added the option to customise the delay.
