# Build multi-etapa: compila con Maven y ejecuta solo el JRE.
# `az acr build` usa la raíz del repo como contexto, así que el jar se construye aquí.

# --- Etapa 1: build ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Descarga dependencias primero para aprovechar la caché de capas.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# --- Etapa 2: runtime ---
FROM eclipse-temurin:17-jre
WORKDIR /deployments

# Usuario sin privilegios.
RUN useradd --system --uid 1001 --no-create-home quarkus

# Layout de fast-jar de Quarkus (copiado por capas, de menos a más cambiante).
COPY --from=build --chown=1001 /build/target/quarkus-app/lib/ ./lib/
COPY --from=build --chown=1001 /build/target/quarkus-app/*.jar ./
COPY --from=build --chown=1001 /build/target/quarkus-app/app/ ./app/
COPY --from=build --chown=1001 /build/target/quarkus-app/quarkus/ ./quarkus/

USER 1001
EXPOSE 8001

# Credenciales BBVA solo por variables de entorno en tiempo de ejecución:
# BBVA_CLIENT_ID, BBVA_CLIENT_SECRET, BBVA_PRIVATE_KEY_BASE64.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /deployments/quarkus-run.jar"]
