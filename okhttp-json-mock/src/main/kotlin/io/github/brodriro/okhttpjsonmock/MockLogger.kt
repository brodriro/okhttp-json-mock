package io.github.brodriro.okhttpjsonmock

/** Receives one line for every request the interceptor answers. */
public fun interface MockLogger {
    /** Called with a single-line [message] such as `GET /api/v1/users -> 200 from api/v1/users.json`. */
    public fun log(message: String)

    public companion object {
        /** Prints every line to standard output, prefixed with `JsonMockServer:`. */
        @JvmField
        public val STDOUT: MockLogger = MockLogger { message -> println("JsonMockServer: $message") }

        /** Discards every line. */
        @JvmField
        public val NONE: MockLogger = MockLogger { }
    }
}
