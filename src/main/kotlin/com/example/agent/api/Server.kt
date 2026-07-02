package com.example.agent.api

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/**
 * Standalone entry point for the REST API server (separate from
 * [com.example.agent.Cli], which remains available for one-off CLI usage).
 *
 * Run via Gradle: `./gradlew run --args="--serve"` is NOT wired here;
 * instead this has its own `main`, invoked directly (e.g. via
 * `./gradlew :run` with a different main class, or `java -cp ... com.example.agent.api.ServerMainKt`).
 * The port is read from the `PORT` environment variable (default 8080).
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureRouting()
}
