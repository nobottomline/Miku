plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":libs:extension-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logback)
    implementation(libs.jsoup)
}
