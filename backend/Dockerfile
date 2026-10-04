# syntax=docker/dockerfile:1
# Multi-stage: Maven builds the fat jar, a slim JRE runs it as a non-root user (Phase 6.4).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Dependencies first: this layer is reused until pom.xml changes.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# Tests run in CI (they need Docker for Testcontainers), not inside the image build.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package && mv target/backend-*.jar /build/app.jar

FROM eclipse-temurin:21-jre-alpine
# OS security fixes published since the base image was built.
RUN apk upgrade --no-cache
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/uploads && chown -R app:app /app
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER app
EXPOSE 8080
# Stay inside the container's memory limit instead of the host's.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
