import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            // JVM 17: the gitlive Firestore SDK ships inline functions built
            // with JVM target 17, which cannot be inlined into 11-target
            // bytecode. Only :shared needs this (composeApp never calls
            // gitlive inline functions directly).
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
            api(libs.koin.core)
            // Ktor multiplatform HTTP client + JSON (Open Food Facts lookup).
            // The engine is provided per-platform (okhttp / darwin); commonMain
            // builds a plain HttpClient and Ktor picks the engine off the
            // classpath at runtime.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            // gitlive Firebase Kotlin SDK — runs in commonMain, calls into the
            // native Firebase SDK on each platform (Android JVM / iOS Obj-C).
            api(libs.gitlive.firebase.auth)
            api(libs.gitlive.firebase.common)
            // Firestore vía GitLive para neveras SHARED.
            api(libs.gitlive.firebase.firestore)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            // Bundled modern SQLite (FTS5 + remove_diacritics 2) wired into the
            // Android driver. Framework SQLite at minSdk 24 predates SQLite 3.27 and
            // does not guarantee FTS5, which our product search now requires.
            implementation(libs.bundled.sqlite.android)
            implementation(libs.mlkit.text.recognition)
            implementation(libs.mlkit.barcode.scanning)
            implementation(libs.ktor.client.okhttp)
            api(libs.koin.android)
            // Pin Firebase Android SDK versions through the BoM so any
            // transitive Firebase artifact pulled in by gitlive resolves
            // to a coherent set.
            implementation(project.dependencies.platform(libs.firebase.bom))
            // Credential Manager + Google Identity Services — used by
            // GoogleSignInHelper.android.kt to launch the One Tap flow.
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.googleid)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // `runTest` for exercising suspend functions (cache / lookup) in
            // commonTest without pulling in a platform test runner.
            implementation(libs.kotlinx.coroutines.test)
        }
        androidUnitTest.dependencies {
            // In-memory JVM SQLite driver so unit tests can exercise the REAL
            // SQLDelight schema (FK / cascade behaviour) without a device.
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

android {
    namespace = "ule.jescuj00.fridgey.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        // Must match the Kotlin jvmTarget above (JVM 17, required by gitlive).
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

sqldelight {
    databases {
        create("FoodSaverDatabase") {
            packageName.set("ule.jescuj00.fridgey.database")
            // Export a versioned snapshot of the schema so migrations can be
            // validated at build time. The schema version is derived from the
            // migration files (`<n>.sqm` migrates from version n -> n+1), so
            // adding `1.sqm` bumps the schema to version 2 and the generated
            // Schema.migrate() runs the ALTER TABLEs on databases still at v1.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            // Fail the build if a migration does not bring a versioned schema
            // snapshot up to the current .sq definition.
            verifyMigrations.set(true)
        }
    }
}
