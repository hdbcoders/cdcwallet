import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
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
        // v6 = 1.0.4-beta: REQ-13 tap-feedback fix only; excludes TicketCard overlay.
        // v7 = 1.0.5-beta: Tap-to-update Play Store launch fix (NEW_TASK flag).
        // v8 = 1.0.6-beta: TicketCard kebab overlay + expiry-row padding.
        // v9 = 1.0.7-beta: red "!" dot on the leading icon while an update is
        //     known (REQ-13) + About page description reword (4 locales).
        // v10 = 1.0.8-beta: senior-friendly wording (4 locales) + pin voucher
        //     + debug/release side-by-side installs (.debug suffix).
        // v11 = 1.0.9-beta: theme overhaul - dark theme picker (Midnight Gold,
        //     Ember Copper), WCAG light palettes, palette renames + legacy-key
        //     migration, JADE tonal mint, pin auto-scroll, splash alignment.
        // v12 = 1.0.10-beta: swatch theme picker (3-per-row cells, ring + check
        //     badge, names in contentDescription) + Aubergine Purple (visible)
        //     and Moss Green (hidden) dark palettes via hiddenInPicker.
        // v13 = 1.0.11-beta: tooling + structure release - lint gate (baseline +
        //     fail-on-new), AGP built-in Kotlin, build/configuration cache,
        //     Gradle wrapper 9.6.1, v2 Compose test-rule migration, four large
        //     refactors (MainActivity/BalanceHero/SettingsScreen/TicketCard
        //     split), dead-asset cleanup, About credits update.
        versionCode = 13
        versionName = "1.0.11-beta"
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
        debug {
            // Side-by-side installs (user request): the debug build gets its
            // own package id so it can coexist with the signed release app on
            // a personal device - separate sandbox, separate DB/prefs, dev
            // seed can never touch real data. The launcher label gets the
            // "Debug" suffix via app/src/debug/res/values*/strings.xml.
            applicationIdSuffix = ".debug"
        }
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
    // Static analysis. The audit's Tier 3 finding was that this project had no
    // lint configuration at all - which is exactly how a dead 1.79 MB font, a
    // stale pre-rename schema directory and an unused test dependency all sat
    // unnoticed.
    //
    // Pre-existing findings are grandfathered into lint-baseline.xml so the gate
    // could be switched on without a mass fix; anything NEW now fails the build.
    // Regenerate the baseline deliberately with
    // `./gradlew :app:updateLintBaseline` after fixing entries.
    //
    // warningsAsErrors is what makes the gate bite: the project has zero lint
    // errors, so abortOnError alone would never trip on a new finding.
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
        warningsAsErrors = true
        // The androidTest set is where most of the avoidable warnings live.
        checkTestSources = true
        // "A newer version of X is available" is information, not a defect. It
        // is the one family of checks guaranteed to go stale on its own: it
        // compares the pinned versions against whatever upstream has published
        // since, so the moment someone else ships a release it reports findings
        // that no code change can fix - and warningsAsErrors above turns those
        // into a red gate. That is exactly how the 1.0.11-beta baseline went
        // bad: all seven errors on 2026-10-08 were version bumps (Gradle
        // 9.8.1, AGP 9.4.1, ksp 2.3.11, core-ktx 1.19.1, navigation 2.10.2,
        // webkit 1.17.1, sqlcipher 4.19.1), on a tree that compiled and passed
        // every test.
        //
        // These three detectors report as informational instead: the heads-up
        // still appears in the lint report, but it cannot fail the build. Real
        // findings - unused resources, accessibility, deprecations - keep
        // failing it, so upgrading a dependency stays a deliberate decision
        // rather than something the gate forces on its own schedule.
        informational += setOf(
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
        )
    }
    sourceSets {
        // Test-fixture consolidation (instrumented-test audit B9): the single
        // contract-faithful FakeVoucherRepository lives in src/sharedTest and
        // is compiled into BOTH the JVM unit-test and the androidTest
        // classpath - Android cannot see `test` sources from androidTest, and
        // the two historical copies had already drifted apart.
        // Built-in Kotlin (AGP 9): Kotlin source dirs must be declared on
        // AndroidSourceSet.kotlin - adding them to .java is unsupported once
        // AGP compiles Kotlin itself, and the sharedTest fixtures would
        // silently vanish from both test variants.
        getByName("test") {
            kotlin.directories += "src/sharedTest/java"
        }
        getByName("androidTest") {
            kotlin.directories += "src/sharedTest/java"
        }
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
// Implemented as a REAL task type wired with finalizedBy - NOT a
// `gradle.taskGraph.afterTask` listener, and not a closure on a plain task.
// Build listeners cannot be recorded by the configuration cache
// ("registration of listener on TaskExecutionGraph.afterTask is unsupported"),
// and a closure that touches build-script members captures the script instance,
// which fails with "cannot serialize Gradle script object references". A task
// type whose inputs are Property / RegularFileProperty objects serializes
// cleanly, so both the test run and the reseed stay configuration-cache safe.
abstract class ReseedDebugDataAfterConnectedTests : DefaultTask() {

    /** Debug applicationId (".debug" suffix) - every adb call targets this package. */
    @get:Input
    abstract val debugAppId: Property<String>

    /** Fully-qualified debug activity: `pkg/.MainActivity` would resolve against the suffixed package and fail. */
    @get:Input
    abstract val debugActivity: Property<String>

    /** -PandroidTestSerial, else ANDROID_SERIAL; absent means plain `adb` (single-device only). */
    @get:Optional
    @get:Input
    abstract val deviceSerial: Property<String>

    /** The debug APK to reinstall. @Internal: it already exists via the test run's own dependency graph. */
    @get:Internal
    abstract val debugApk: RegularFileProperty

    private fun adbCommand(vararg args: String): List<String> = buildList {
        add("adb")
        if (deviceSerial.isPresent) {
            add("-s")
            add(deviceSerial.get())
        }
        addAll(args.toList())
    }

    private fun adb(vararg args: String): Int =
        ProcessBuilder(adbCommand(*args)).inheritIO().start().waitFor()

    /** adb with captured output, for the calls whose reply is the readiness signal. */
    private fun adbCapture(vararg args: String): Pair<Int, String> {
        val proc = ProcessBuilder(adbCommand(*args)).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().use { it.readText() }
        return proc.waitFor() to output
    }

    @TaskAction
    fun reseed() {
        // finalizedBy fires even when the test task was SKIPPED (e.g. no attached
        // device), which the old listener deliberately did not do - so bail out
        // unless there is a device actually available to talk to.
        val (devicesExit, devicesOut) = runCatching { adbCapture("devices") }.getOrDefault(-1 to "")
        val deviceAttached = devicesExit == 0 &&
            devicesOut.lineSequence().any { it.trim().matches(Regex("""\S+\s+device""")) }
        if (!deviceAttached) {
            println("Skipped reseed after connected tests: no attached device.")
            return
        }
        val apk = debugApk.get().asFile
        runCatching {
            report("Reinstalled debug APK after connected tests", adb("install", "-r", apk.absolutePath))
            // The uninstall above wiped the app data (including the seeded DB).
            // SeedDevDataReceiver is registered DYNAMICALLY by DebugVoucherApp
            // (API 36 silently drops implicit broadcasts to manifest-declared
            // receivers), so the app must be running for the broadcast to reach
            // it. `am start -W` does not return until the activity is resumed,
            // which can only happen after Application.onCreate registered the
            // receiver - a deterministic wait, replacing a fixed 2.5 s sleep.
            val (startExit, startOut) = adbCapture("shell", "am", "start", "-W", "-n", debugActivity.get())
            val startSummary = startOut.lineSequence().lastOrNull { it.isNotBlank() }?.trim() ?: "no output"
            report("Launched debug app after connected tests", startExit, startSummary)
            // SeedDevDataReceiver does its database work behind goAsync() and
            // calls pendingResult.finish() only once it is done, so the
            // broadcast is not completed - and `am broadcast` does not return -
            // until the seeding has landed. That replaces a fixed 2 s sleep.
            report(
                "Reseeded dev data after connected tests",
                adb(
                    "shell", "am", "broadcast", "-a",
                    "com.hdbcoders.cdcwallet.action.SEED_DEV_DATA",
                ),
            )
            report("Stopped debug app after connected tests", adb("shell", "am", "force-stop", debugAppId.get()))
            report("Relaunched debug app after connected tests", adb("shell", "am", "start", "-n", debugActivity.get()))
        }.onFailure { println("WARN: reinstall/reseed-after-tests failed: $it") }
    }

    /**
     * Logs one step and flags a non-zero adb exit as a warning. Fail-soft by
     * design - the reseed never fails the build, it only reports what happened -
     * but a mis-targeted device should be visible rather than looking like
     * success: `adb devices` ignores -s, so the pre-check above cannot catch a
     * wrong -PandroidTestSerial / ANDROID_SERIAL.
     */
    private fun report(step: String, exit: Int, detail: String? = null) {
        val suffix = if (detail.isNullOrBlank()) "" else ", $detail"
        if (exit == 0) {
            println("$step (adb exit 0$suffix)")
        } else {
            println("WARN: $step FAILED (adb exit $exit$suffix)")
        }
    }
}

val reseedDebugDataAfterConnectedTests = tasks.register<ReseedDebugDataAfterConnectedTests>(
    "reseedDebugDataAfterConnectedTests",
) {
    group = "verification"
    description = "Reinstalls the debug APK and reseeds dev fixtures after connectedDebugAndroidTest."
    val suffixDebugAppId = "${android.defaultConfig.applicationId}.debug"
    debugAppId.set(suffixDebugAppId)
    debugActivity.set("$suffixDebugAppId/com.hdbcoders.cdcwallet.MainActivity")
    debugApk.set(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    val serial = providers.gradleProperty("androidTestSerial")
        .orElse(providers.environmentVariable("ANDROID_SERIAL"))
    if (serial.isPresent) {
        deviceSerial.set(serial)
    }
}

// finalizedBy, unlike the old listener, also runs when the test task FAILS -
// which is what we want: the device keeps its seeded state after a failed run.
tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    finalizedBy(reseedDebugDataAfterConnectedTests)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Fail-fast: never produce an unsigned release artifact. The check runs only
// when a release bundle/APK is actually requested, so debug builds and tests
// stay unaffected when keystore/ is absent (e.g. a fresh clone).
//
// The boolean is read at configuration time into a local, so the task action
// captures a plain Boolean. Reading `keystoreProperties` directly inside the
// action captured this build script's own state, which the configuration cache
// cannot serialize: it failed :app:assembleRelease / :packageRelease (and
// bundleRelease) with "cannot serialize Gradle script object references".
tasks.matching {
    it.name.contains("Release") &&
        (it.name.startsWith("bundle") || it.name.startsWith("assemble") || it.name.startsWith("package"))
}.configureEach {
    val keystorePresent = keystoreProperties.isNotEmpty()
    doFirst {
        check(keystorePresent) {
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
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    // AGENTS.md: navigation in tests goes through UIAutomator selectors
    // (By.text / By.desc), never raw screen-coordinate taps.
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
