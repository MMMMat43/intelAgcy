plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Парсинг Java-кода
    implementation("com.github.javaparser:javaparser-core:3.25.10")

    // Генерация Kotlin-кода
    implementation("com.squareup:kotlinpoet:1.16.0")

    // HTTP-клиент
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Работа с JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")

    // Ktor для REST API
    implementation("io.ktor:ktor-server-netty:2.3.6")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.6")
    implementation("io.ktor:ktor-serialization-jackson:2.3.6")

    // Логирование (опционально)
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Тестирование
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

application {
    mainClass.set("com.example.agent.CliKt") // для CLI
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}


