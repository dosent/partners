FROM gradle:8.14.4-jdk21-alpine AS builder
WORKDIR /workspace
COPY mcp-server/ mcp-server/
RUN gradle -p mcp-server bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S -G app app
COPY --from=builder /workspace/mcp-server/build/libs/partner-mcp-server-*.jar /app/mcp-server.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/mcp-server.jar"]
