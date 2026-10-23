plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

// The apps include this build with includeBuild() and depend on
// "org.coloradomesh.meshcore:protocol" and "org.coloradomesh.meshcore:ble"; Gradle matches
// those to the projects here by this group and each project's name.
subprojects {
    group = "org.coloradomesh.meshcore"
}
