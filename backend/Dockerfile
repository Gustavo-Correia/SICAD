FROM maven:3.9.9-eclipse-temurin-24 AS build  
WORKDIR /build

COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests dependency:copy-dependencies -DoutputDirectory=target/dependency

FROM eclipse-temurin:24-jre-alpine
WORKDIR /app

RUN apk update && apk add --no-cache curl
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /build/target/classes /app/classes
COPY --from=build /build/target/dependency /app/dependency

COPY --from=build /build/target/backend-1.0.jar /app/app.jar

EXPOSE 8080 

HEALTHCHECK --interval=30s --timeout=30s --start-period=5s --retries=3 CMD [ "curl -f http://localhost:8080/health || exit 1" ]

USER appuser

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-cp", "/app/classes:/app/dependency/*", "com.sicad.Main"]

