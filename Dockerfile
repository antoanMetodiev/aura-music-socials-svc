# Render has no native Java runtime, so the service ships as a Docker image.
# Build context = the repo root (Render "Root Directory" left empty).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Wrapper + build scripts first so the dependency download is cached between deploys.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN sh gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN sh gradlew --no-daemon bootJar -x test \
 && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar
# Render injects PORT; application.yml reads it (server.port: ${PORT:8087}).
# MaxRAMPercentage keeps the heap inside small instances (free tier = 512 MB).
# Config comes from real env vars (see .env.example) — the .env file is never in the image.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
