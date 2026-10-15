import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.maven.publish)
}

group = "io.github.brodriro"
version = "4.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    explicitApi()
    // Fails the build when the public API changes without the dump in api/ being updated.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()
    // Language, API and standard library stay on an older Kotlin so that projects
    // with an older compiler can still read the published metadata.
    coreLibrariesVersion = "2.0.21"
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
        languageVersion = KotlinVersion.KOTLIN_2_0
        apiVersion = KotlinVersion.KOTLIN_2_0
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

// check fails when line coverage of the library drops below this floor.
kover {
    reports {
        verify {
            rule {
                minBound(85)
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // SampleMocksTest reads the example mocks of the sample app.
    inputs.dir(rootProject.file("app/src/main/assets")).withPathSensitivity(PathSensitivity.RELATIVE)
}

// The Apache License asks redistributions to carry the license and the attribution notice.
tasks.withType<Jar>().configureEach {
    from(rootProject.files("LICENSE", "NOTICE")) {
        into("META-INF")
    }
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
                email = "brian.rodriro@gmail.com"
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
