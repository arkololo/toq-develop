# syntax=docker/dockerfile:1
FROM maven:3.9.12-eclipse-temurin-21@sha256:c3c9d3ac4ce8431a3995c0318b8d390f448e693dd4fabc16e9b68d2e1f3d7b46 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY services/user-service/pom.xml services/user-service/pom.xml
COPY services/recipe-service/pom.xml services/recipe-service/pom.xml
COPY services/nutrition-service/pom.xml services/nutrition-service/pom.xml
COPY services/planner-service/pom.xml services/planner-service/pom.xml
COPY services/social-service/pom.xml services/social-service/pom.xml
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -ntp -pl services/planner-service -am dependency:go-offline

COPY services/planner-service/src services/planner-service/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -ntp -pl services/planner-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine@sha256:1a29e1fe337eb28b5bec30f0ee8ed29f0ff80ab6f75dcf9313efe82911065a52 AS runtime
RUN apk add --no-cache curl \
    && addgroup -g 10001 toq \
    && adduser -D -H -u 10001 -G toq toq
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/services/planner-service/target/app.jar app.jar
USER 10001:10001
EXPOSE 8080 8081
HEALTHCHECK --interval=15s --timeout=6s --start-period=60s --retries=5 \
    CMD curl --fail --silent --max-time 5 http://127.0.0.1:8081/actuator/health/readiness || exit 1
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
