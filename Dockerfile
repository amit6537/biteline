# syntax=docker/dockerfile:1
# ─────────────────────────────────────────────────────────────
# Food Delivery Backend — production image
#
# CONCEPT: Multi-stage build
#   Stage 1 (build)   : full JDK + Maven → compiles the code, makes the jar
#   Stage 2 (extract) : splits the jar into layers (see below)
#   Stage 3 (runtime) : only a small JRE + the app. No compiler, no Maven,
#                       no source code → smaller and safer image.
#
# CONCEPT: Layered jar
#   A Spring Boot jar = ~100 MB of libraries that rarely change + a few
#   hundred KB of OUR code that changes on every commit. Docker caches
#   each COPY as a layer, so we copy libraries first and our code last:
#   a code change rebuilds/pushes only the tiny last layer.
#
# Build:  docker build -t food-delivery-backend .
# Run:    docker compose --profile app up -d     (see docker-compose.yml)
# ─────────────────────────────────────────────────────────────

# ── Stage 1: build ───────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Maven wrapper + pom first: dependency downloads are cached until pom.xml changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw

COPY src src
# --mount=type=cache keeps ~/.m2 between builds (BuildKit), so dependencies
# are downloaded once, not on every build. Tests run in CI, not here.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp package -DskipTests \
 && cp target/backend-*.jar application.jar

# ── Stage 2: split the jar into layers ───────────────────────
FROM eclipse-temurin:21-jre AS extract
WORKDIR /extract
COPY --from=build /workspace/application.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ── Stage 3: runtime ─────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

# Never run as root inside the container
RUN groupadd --system app && useradd --system --gid app --no-create-home app

# Least-changing layers first → best Docker cache reuse
COPY --from=extract /extract/extracted/dependencies/ ./
COPY --from=extract /extract/extracted/spring-boot-loader/ ./
COPY --from=extract /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract /extract/extracted/application/ ./

USER app
EXPOSE 8080

# Container-aware memory: use up to 75% of the container's RAM limit
# (the JVM default of 25% wastes most of a small container).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# `exec` makes Java PID 1, so `docker stop` (SIGTERM) reaches it and Spring
# shuts down gracefully instead of being killed after 10 seconds.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar application.jar"]
