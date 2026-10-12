plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ir.mirrajabi.okhttpjsonmock.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "ir.mirrajabi.okhttpjsonmock.sample"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":okhttpjsonmock"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)

    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.retrofit.adapter.rxjava2)
    implementation(libs.rxjava)
    implementation(libs.rxandroid)
}
