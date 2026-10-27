# Builds and runs the gateway. Used by docker-compose.yml.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY gateway-domain gateway-domain
COPY gateway-application gateway-application
COPY gateway-adapters gateway-adapters
COPY gateway-api gateway-api
COPY gateway-app gateway-app
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 gateway
COPY --from=build /workspace/gateway-app/target/gateway-app-*.jar /app/gateway.jar
USER gateway
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/gateway.jar"]
