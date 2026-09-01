# ── Build stage ──────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Cache dependencies separately from source so code edits don't re-download.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src/ src/
RUN mvn -B clean package -DskipTests

# ── Runtime stage ────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# wget se usa en el HEALTHCHECK de abajo; en la imagen alpine viene por
# BusyBox, pero se declara explícito para que un cambio de imagen base no
# rompa la comprobación en silencio.
RUN apk add --no-cache wget \
    && addgroup -S sicot && adduser -S sicot -G sicot
COPY --from=build /build/target/sicot-backend-*.jar app.jar
RUN chown sicot:sicot app.jar
USER sicot

EXPOSE 8080

# ── Ajustes de JVM ───────────────────────────────────────────────────────────
#
# MaxRAMPercentage=75 — sin esto la JVM usa su valor por defecto del 25 %, es
# decir 256 MB de heap dentro del contenedor de 1 GB que define
# docker-compose.yml. Tres cuartas partes de la memoria asignada quedaban sin
# usar mientras el proceso se quedaba corto: subir documentos de hasta 20 MB
# con 256 MB de heap deja muy poco margen. El 25 % restante es para el resto
# de la memoria del proceso (metaspace, pilas de hilos, buffers directos), que
# no sale del heap.
#
# ExitOnOutOfMemoryError — un OutOfMemoryError deja la JVM en un estado del que
# no se recupera: los hilos que sobreviven siguen fallando de formas raras y el
# contenedor se queda "arriba" atendiendo peticiones que no puede completar.
# Terminar el proceso hace que Docker lo reinicie (restart: unless-stopped) y
# que el healthcheck refleje la realidad.
#
# UseContainerSupport está activo por defecto desde hace varias versiones; se
# escribe igual porque es la premisa de la que depende MaxRAMPercentage.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# El healthcheck vive también aquí, no solo en docker-compose.yml: así la
# imagen se comporta igual cuando se despliega con otra herramienta
# (docker run, Swarm, Kubernetes) que no lea ese archivo.
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

# Forma "shell" para que $JAVA_OPTS se expanda; con la forma exec se pasaría
# como una sola cadena literal y la JVM la rechazaría.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
