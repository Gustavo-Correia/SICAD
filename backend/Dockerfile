FROM maven:3.9.9-eclipse-temurin-24 AS build
WORKDIR /build

COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests dependency:copy-dependencies -DoutputDirectory=target/dependency

FROM eclipse-temurin:24-jre-alpine
WORKDIR /app

RUN apk add --no-cache netcat-openbsd
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /build/target/classes /app/classes
COPY --from=build /build/target/dependency /app/dependency

EXPOSE 5000

HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=5 \
  CMD nc -z localhost 5000 || exit 1

USER appuser

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-cp", "/app/classes:/app/dependency/*", "com.sicad.Main"]
