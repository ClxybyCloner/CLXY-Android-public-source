import java.util.Properties

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// The release key, from android/key.properties (never in Git: CI writes it
// from the repository's secrets). storeFile, storePassword, keyAlias,
// keyPassword.
val releaseKey = Properties().apply {
    val file = rootProject.file("key.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Keep the native namespace for manifest and JNI bindings, but install as CLXY.
val clxyApplicationId = "com.clxy.android"
val clxyAppLabel = "CLXY"

android {
    namespace = "com.arman.firefoxvpn"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = "30.0.16248370"

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // Native packet forwarder used by the VPN bridge.
    externalNativeBuild {
        ndkBuild {
            path = file("../hev-socks5-tunnel/Android.mk")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = clxyApplicationId
        manifestPlaceholders["appLabel"] = clxyAppLabel
        // The Go AAR is built for API 24 (gomobile bind -androidapi 24).
        minSdk = maxOf(24, flutter.minSdkVersion)
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName

        externalNativeBuild {
            ndkBuild {
                targets += "hev-socks5-tunnel"
                arguments += listOf("REV_ID=2.19.3", "APP_SUPPORT_FLEXIBLE_PAGE_SIZES=true")
                // Bind native methods to Tun2Socks in the retained namespace.
                cFlags += listOf("-O3", "-DPKGNAME=com/arman/firefoxvpn", "-DCLSNAME=Tun2Socks")
            }
        }
    }

    signingConfigs {
        getByName("debug") {
            // Build hosts can select the established CLXY test key without
            // copying it or writing signing credentials into this source tree.
            System.getenv("CLXY_ANDROID_DEBUG_KEYSTORE")
                ?.takeIf { it.isNotBlank() }
                ?.let { storeFile = file(it) }
        }
        create("release") {
            if (!releaseKey.isEmpty) {
                storeFile = file(releaseKey.getProperty("storeFile"))
                storePassword = releaseKey.getProperty("storePassword")
                keyAlias = releaseKey.getProperty("keyAlias")
                keyPassword = releaseKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Without a configured private key, keep continuity with CLXY's
            // test builds. The public release key still takes precedence.
            signingConfig = signingConfigs.getByName(if (releaseKey.isEmpty) "debug" else "release")
            // Keep JNI entry points from R8; hev registers them by name at load time.
            proguardFiles("proguard-rules.pro")
        }
    }
}

// Flutter's plugin points CMake at an empty project unless it sees a CMake
// build. This module uses ndk-build; avoid configuring two native builds.
androidComponents {
    finalizeDsl { it.externalNativeBuild.cmake.path = null }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

dependencies {
    // Generated from the pinned Go mobile package in core/.
    implementation(files("libs/core.aar"))
    // Android WebView proxy override for the optional in-app sign-in page.
    implementation("androidx.webkit:webkit:1.15.0")
    testImplementation("junit:junit:4.13.2")
}
