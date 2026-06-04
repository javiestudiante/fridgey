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
            jvmTarget.set(JvmTarget.JVM_11)
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
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
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
        }
    }
}

android {
    namespace = "ule.jescuj00.fridgey.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
