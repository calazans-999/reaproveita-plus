# Reaproveita+ - Etapa 10. Multi-stage: compila sem copiar credenciais.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw ./mvnw
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --gid 10001 reaproveita && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin reaproveita
COPY --from=build --chown=10001:10001 /workspace/target/reaproveita-plus-0.0.1-SNAPSHOT.jar /app/reaproveita-plus.jar
ENV SERVER_PORT=8082 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8082
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/reaproveita-plus.jar"]
