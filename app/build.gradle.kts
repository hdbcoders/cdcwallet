import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.cdcvouchers"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cdcvouchers"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        // Speed/quietness for connected tests: disable system animations so
        // Compose test waits are deterministic.
        animationsDisabled = true
    }
}

// AGP 9 removed the old `android.experimental.androidTest.uninstallAfterTest`
// knob, and connectedDebugAndroidTest uninstalls the app + test APK at the
// end of its own action — there is no DSL/task to stop it. To keep the app
// installed after a test run (so device state survives verification runs),
// reinstall the debug APK as soon as the test task finishes, including on
// failure. Target device: -PandroidTestSerial=<serial> if set, else the
// ANDROID_SERIAL env var, else plain `adb install` (single-device only).
tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    val thisTask = this
    fun reinstallAfterTests() {
        val apk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        val serial = providers.gradleProperty("androidTestSerial").orNull
            ?: System.getenv("ANDROID_SERIAL")
        runCatching {
            val proc = ProcessBuilder(
                buildList {
                    add("adb")
                    if (serial != null) {
                        add("-s")
                        add(serial)
                    }
                    addAll(listOf("install", "-r", apk.absolutePath))
                },
            ).inheritIO().start()
            println("Reinstalled debug APK on $serial after connected tests (adb exit ${proc.waitFor()})")
        }.onFailure { println("WARN: reinstall-after-tests failed: $it") }
    }
    // afterTask covers both success and failure (doLast would not run on
    // failure); never masks the original result, never runs when skipped.
    gradle.taskGraph.afterTask(closureOf<Task> {
        if (this == thisTask && !state.skipped) reinstallAfterTests()
    })
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    constraints {
        // androidx.concurrent is an atomic group — all its artifacts must resolve
        // to the same version. core 1.16.0 only requests 1.0.0 and profileinstaller
        // 1.1.0, while the androidTest graph (test core/espresso) needs 1.2.0.
        // Pinning the group avoids AGP's consistent-resolution lint lock conflict.
        implementation("androidx.concurrent:concurrent-futures:1.2.0")
        implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
    }
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.net.zetetic.sqlcipher)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
