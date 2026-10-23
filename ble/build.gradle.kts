plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.coloradomesh.meshcore.ble"
    compileSdk = 34

    defaultConfig {
        // The lowest of the apps that use it: MeshCore TAK Bridge runs on Android 8.
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // RadioSession takes a CompanionClient.Listener, so apps see the protocol types too.
    api(project(":protocol"))
}
