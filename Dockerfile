# Multi-stage build for IntelligentTestAgent REST API server.
#
# Stage 1 (build): compiles the Kotlin/Gradle project and assembles a
# runnable distribution (installDist) containing our jar plus all runtime
# dependencies as plain jars in lib/.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Copy only what is needed to resolve dependencies and build first, so
# Docker layer caching can skip re-downloading dependencies when only
# source files change.
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew

COPY src/ src/

RUN chmod +x gradlew && ./gradlew --no-daemon installDist -x test

# Stage 2 (runtime): full JDK image running the REST API server.
#
# A full JDK (not just a JRE) is required at runtime, not only at build
# time: /generate-tests compiles the analyzed Java source in memory via
# javax.tools.ToolProvider.getSystemJavaCompiler() (see
# com.example.agent.execution.InMemoryJavaCompiler) so it can actually
# execute each generated scenario and produce real assertEquals/assertThrows
# test bodies instead of TODO placeholders. That compiler API returns null
# on a JRE-only runtime, so a JDK image is used here on purpose (slightly
# larger image, but required for this feature to work).
FROM eclipse-temurin:17-jdk AS runtime
WORKDIR /app

COPY --from=build /workspace/build/install/IntelligentTestAgent/lib /app/lib

EXPOSE 8080
ENV PORT=8080

# application{} in build.gradle.kts points mainClass at the CLI
# (com.example.agent.CliKt), not the server, so we deliberately bypass the
# generated start script and launch the server entry point directly via a
# wildcard classpath (requires the shell form of ENTRYPOINT to expand '*').
ENTRYPOINT ["sh", "-c", "java -cp '/app/lib/*' com.example.agent.api.ServerKt"]
