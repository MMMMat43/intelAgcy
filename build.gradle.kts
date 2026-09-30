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
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")

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
    testImplementation("io.ktor:ktor-server-test-host:2.3.6")
    testImplementation("io.ktor:ktor-client-content-negotiation:2.3.6")
}

application {
    mainClass.set("com.example.agent.CliKt") // для CLI
}

// Отдельный таск для запуска REST API сервера (Task 07), чтобы не менять
// основной mainClass приложения (он остаётся CLI). Запуск: `./gradlew runServer`.
tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Runs the REST API server (com.example.agent.api.ServerKt)"
    mainClass.set("com.example.agent.api.ServerKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
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


