plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    // MeshPicture takes and returns protocol types (Contact, Channel, messages), so apps see them too.
    api(project(":protocol"))
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
