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

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 CMD [ "sh", "-c", "curl -f http://localhost:8081/health || exit 1" ]

USER appuser

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-cp", "/app/classes:/app/dependency/*", "com.sicad.Main"]

# ENTRYPOINT ["java", --> Esse comando inicia a aplicação Java.
# "-XX:+UseContainerSupport", --> Essa opção habilita o suporte a contêineres
# "-XX:MaxRAMPercentage=75.0", --> Essa opção define a porcentagem máxima de RAM 
# "-XX:+ExitOnOutOfMemoryError", --> Essa opção para a aplicação se não houver memória suficiente
# "-cp", --> Classe de aplicação e dependências
# "/app/classes:/app/dependency/*", --> Uma especificação das classes e dependências
# "com.sicad.Main"] --> O nome da classe principal da aplicação Java a ser executada.