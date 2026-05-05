import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
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
            // gitlive Firebase Kotlin SDK — runs in commonMain, calls into the
            // native Firebase SDK on each platform (Android JVM / iOS Obj-C).
            api(libs.gitlive.firebase.auth)
            api(libs.gitlive.firebase.common)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.mlkit.text.recognition)
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
        }
    }
}
