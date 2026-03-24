plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    application
}

application {
    mainClass.set("miku.server.ApplicationKt")
}

ktor {
    fatJar {
        archiveFileName.set("miku-server.jar")
    }
}

dependencies {
    implementation(project(":libs:extension-api"))
    implementation(project(":libs:android-compat"))
    implementation(project(":libs:extension-runner"))
    implementation(project(":libs:domain"))
    implementation(project(":libs:database"))

    implementation(libs.bundles.ktor.server)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    implementation(libs.graphql.kotlin.schema)

    implementation(libs.argon2)
    implementation(libs.jwt)
    implementation(libs.caffeine)
    implementation(libs.logback)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(platform("org.junit:junit-bom:5.12.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.bundles.testing.integration)
    testImplementation(libs.ktor.server.test)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.json)
    testImplementation(libs.postgresql)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("miku.test", "true")
}
