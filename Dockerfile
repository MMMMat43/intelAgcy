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

# Stage 2 (runtime): minimal JRE image running the REST API server.
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

COPY --from=build /workspace/build/install/IntelligentTestAgent/lib /app/lib

EXPOSE 8080
ENV PORT=8080

# application{} in build.gradle.kts points mainClass at the CLI
# (com.example.agent.CliKt), not the server, so we deliberately bypass the
# generated start script and launch the server entry point directly via a
# wildcard classpath (requires the shell form of ENTRYPOINT to expand '*').
ENTRYPOINT ["sh", "-c", "java -cp '/app/lib/*' com.example.agent.api.ServerKt"]
