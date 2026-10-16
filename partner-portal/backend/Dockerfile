FROM gradle:8.14.4-jdk21-alpine AS builder
USER root
RUN mkdir -p /workspace && chown gradle:gradle /workspace
WORKDIR /workspace
COPY --chown=gradle:gradle . .
USER gradle
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
