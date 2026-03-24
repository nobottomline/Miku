plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":libs:extension-api"))
    api(project(":libs:android-compat"))
    implementation(libs.okhttp)
    implementation(libs.okhttp.brotli)
    implementation(libs.okhttp.logging)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.caffeine)
    implementation(libs.logback)

    // APK/DEX handling
    implementation(libs.dex2jar.translator)
    implementation(libs.dex2jar.tools)
    implementation(libs.apk.parser)
}
