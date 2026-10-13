plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

// The version under test is the one the library build declares.
val libraryVersion = Regex("""^version = "(.*)"$""", RegexOption.MULTILINE)
    .find(file("../okhttp-json-mock/build.gradle.kts").readText())!!
    .groupValues[1]

dependencies {
    implementation("io.github.brodriro:okhttp-json-mock:$libraryVersion")
    // Optional: force another OkHttp, for example 5.x, over the one the library asks for.
    providers.gradleProperty("okhttpVersion").orNull?.let { implementation("com.squareup.okhttp3:okhttp:$it") }
}

application {
    mainClass = "MainKt"
}
