import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing (Play upload key). Credentials live in keystore/ which is
// gitignored - never commit the keystore or its passwords. `keystore
// -genkeypair` once, keep backups; a lost upload key means a lost app.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.hdbcoders.cdcwallet"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hdbcoders.cdcwallet"
        minSdk = 24
        targetSdk = 36
        // versionCode 1 was already consumed by the first Play upload; every
        // subsequent upload needs a strictly higher code.
        // v2 = first closed-test upload; v3 = cold-start crash fix (1.0.1-beta);
        // v4 = Play language-split fix (1.0.2-beta): all locales in base module;
        // v5 = REQ-13 update check + TicketCard layout (1.0.3-beta).
        versionCode = 5
        versionName = "1.0.3-beta"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = rootProject.file("keystore/${keystoreProperties["storeFile"]}")
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // Only wire the signing config when the keystore exists; the
            // task-level check below refuses to actually build unsigned.
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // R8 code shrinking (spec 01 §1.6): enabled behind the gated
            // verification - assembleRelease must build and the release APK
            // is smoke-tested on-device (add + WebView detail + backup
            // export/import) before shipping. Any keep rule needed goes into
            // proguard-rules.pro with a comment; do not weaken rules broadly.
            isMinifyEnabled = true
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
    // In-app language switcher (REQ-11): Play's default per-language split
    // delivery only installs the device-language split (e.g. split_config.en),
    // so values-ms/values-ta/values-zh-rCN resources are missing at runtime and
    // the switcher falls back to English on Play-installed builds. Ship all
    // four languages inside the base module instead.
    bundle {
        language {
            enableSplit = false
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
// installed AND seeded after a test run (so device state survives
// verification runs), reinstall the debug APK and reseed the dev fixtures as
// soon as the test task finishes, including on failure. Target device:
// -PandroidTestSerial=<serial> if set, else the ANDROID_SERIAL env var, else
// plain `adb` (single-device only).
tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    val thisTask = this
    fun adb(vararg args: String): Int {
        val serial = providers.gradleProperty("androidTestSerial").orNull
            ?: System.getenv("ANDROID_SERIAL")
        val proc = ProcessBuilder(
            buildList {
                add("adb")
                if (serial != null) {
                    add("-s")
                    add(serial)
                }
                addAll(args.toList())
            },
        ).inheritIO().start()
        return proc.waitFor()
    }
    fun reinstallAndReseedAfterTests() {
        val apk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        runCatching {
            val installExit = adb("install", "-r", apk.absolutePath)
            println("Reinstalled debug APK after connected tests (adb exit $installExit)")
            // The uninstall above wiped the app data (including the seeded
            // DB). SeedDevDataReceiver is registered DYNAMICALLY by
            // DebugVoucherApp (API 36 silently drops implicit broadcasts to
            // manifest-declared receivers), so the app must be running for
            // the broadcast to reach it. Launch, let it register, then
            // broadcast (which also re-enables auto-seed), and relaunch so
            // the foreground app reflects the seeded data.
            adb("shell", "am", "start", "-n", "com.hdbcoders.cdcwallet/.MainActivity")
            Thread.sleep(2500)
            val seedExit = adb(
                "shell", "am", "broadcast", "-a",
                "com.hdbcoders.cdcwallet.action.SEED_DEV_DATA",
            )
            println("Reseeded dev data after connected tests (adb exit $seedExit)")
            Thread.sleep(2000)
            adb("shell", "am", "force-stop", "com.hdbcoders.cdcwallet")
            adb("shell", "am", "start", "-n", "com.hdbcoders.cdcwallet/.MainActivity")
        }.onFailure { println("WARN: reinstall/reseed-after-tests failed: $it") }
    }
    // afterTask covers both success and failure (doLast would not run on
    // failure); never masks the original result, never runs when skipped.
    gradle.taskGraph.afterTask(closureOf<Task> {
        if (this == thisTask && !state.skipped) reinstallAndReseedAfterTests()
    })
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Fail-fast: never produce an unsigned release artifact. The check runs only
// when a release bundle/APK is actually requested, so debug builds and tests
// stay unaffected when keystore/ is absent (e.g. a fresh clone).
tasks.matching {
    it.name.contains("Release") &&
        (it.name.startsWith("bundle") || it.name.startsWith("assemble") || it.name.startsWith("package"))
}.configureEach {
    doFirst {
        check(keystoreProperties.isNotEmpty()) {
            "Release signing config missing: keystore/keystore.properties not found. " +
                "Create it with a generated keystore (keytool -genkeypair), see the release notes."
        }
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.net.zetetic.sqlcipher)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // Play In-App Updates (REQ-13): queries the Play Store for a newer app
    // version via Play Services (a binder call, not a network request). The
    // update check is user- or app-open-triggered only - never scheduled.
    implementation(libs.play.app.update)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    // AGENTS.md: navigation in tests goes through UIAutomator selectors
    // (By.text / By.desc), never raw screen-coordinate taps.
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
