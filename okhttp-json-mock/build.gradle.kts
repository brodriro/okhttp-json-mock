import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

group = "io.github.brodriro"
version = "4.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

dependencies {
    api(libs.okhttp)
    implementation(libs.gson)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    coordinates(group.toString(), "okhttp-json-mock", version.toString())
    publishToMavenCentral()

    // Maven Central requires signed releases; local and snapshot publishing work without a key.
    val hasSigningKey = providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.keyId").isPresent
    if (hasSigningKey) {
        signAllPublications()
    }

    pom {
        name = "okhttp-json-mock"
        description = "Mock OkHttp/Retrofit responses with local JSON files. " +
            "Fork of mirrajabi/okhttp-json-mock that continues from 3.0."
        inceptionYear = "2017"
        url = "https://github.com/brodriro/okhttp-json-mock"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "brodriro"
                name = "Brian Rodriguez"
                url = "https://github.com/brodriro"
            }
        }
        contributors {
            contributor {
                name = "Mohammad Mirrajabi"
                url = "https://github.com/mirrajabi"
                roles = listOf("Original author")
            }
            contributor {
                name = "Philipp Fahlteich"
                url = "https://github.com/Faltenreich"
            }
        }
        scm {
            url = "https://github.com/brodriro/okhttp-json-mock"
            connection = "scm:git:git://github.com/brodriro/okhttp-json-mock.git"
            developerConnection = "scm:git:ssh://git@github.com/brodriro/okhttp-json-mock.git"
        }
    }
}
