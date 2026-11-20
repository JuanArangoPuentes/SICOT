# ── Build stage ──────────────────────────────────────────────────────────────
# Imágenes base fijadas por versión exacta, no por rama: una actualización llega
# como PR de Dependabot (ecosistema docker), se revisa y el CI escanea la imagen
# resultante con Trivy. Con "25-jre-alpine" cambiaban por debajo sin revisión.
FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /build

# Cache dependencies separately from source so code edits don't re-download.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src/ src/
RUN mvn -B clean package -DskipTests

# ── Runtime stage ────────────────────────────────────────────────────────────
FROM eclipse-temurin:25.0.4.1_1-jre-alpine
WORKDIR /app

# wget se usa en el HEALTHCHECK de abajo; en la imagen alpine viene por
# BusyBox, pero se declara explícito para que un cambio de imagen base no
# rompa la comprobación en silencio.
#
# La base trae libexpat 2.8.5-r0, con CVE-2026-77214 (HIGH): lectura fuera del
# búfer al parsear XML. Alpine ya publicó 2.9.0-r0, así que el arreglo es
# gratis, y esperar a que Temurin reconstruya su imagen dejaba cerrado el
# escaneo de imágenes, que es un chequeo obligatorio (CI del 18-10-2026). Se
# sube SOLO ese paquete, por lo mismo que el libtiff del frontend: el resto de
# la imagen sigue siendo la que fija la etiqueta. Cuando la imagen de Temurin
# traiga 2.9.0-r0 o posterior, la primera línea sobra y se quita.
#
# brotli-libs, por lo mismo (CI del 23-10-2026): la base trae 1.2.0-r1, con
# CVE-2026-111497 (HIGH, escritura fuera de límites en el decodificador), y
# Alpine ya publicó 1.2.1-r0.
#
# libexpat otra vez (CI del 05-11-2026): 2.9.0-r0 trae tres HIGH de denegación
# de servicio (CVE-2026-113351, -117610 y -118370) y Alpine publicó 2.10.0-r0.
# Hay que subir el mínimo y no basta con volver a construir: `apk add` con una
# condición que lo instalado ya cumple no actualiza nada.
#
# libfontenc (CI del 20-11-2026): la base trae 1.1.9-r0, con CVE-2026-109908
# (HIGH, fallo de validación al parsear un archivo de fuentes), y Alpine
# publicó 1.1.10-r0 en la rama v3.24. Viene con la pila de fuentes de la JRE,
# que SICOT usa de verdad: PDFBox mide y dibuja texto al armar los documentos
# formales y POI al generar el XLSX de la solicitud de pedido, así que no es un
# paquete que se pueda quitar en vez de parchear.
RUN apk add --no-cache "libexpat>=2.10.0-r0" "brotli-libs>=1.2.1-r0" "libfontenc>=1.1.10-r0" \
    && apk add --no-cache wget \
    && addgroup -S sicot && adduser -S sicot -G sicot

# La carpeta de los informes para el técnico, creada aquí y con su dueño.
#
# docker-compose.prod.yml monta en /informes el volumen `sicot_informes`. Si la
# carpeta no existe en la imagen, Docker la crea al montar, y la crea de root:
# el backend, que corre como `sicot`, no puede escribir. Así estuvo el servidor
# del Centro del 11 al 17-11-2026, con «No se pudo escribir el informe en
# /informes» en cada informe y la carpeta vacía; solo llegaba el correo.
#
# Con la carpeta en la imagen, Docker copia su contenido y su dueño a un volumen
# con nombre cuando lo monta vacío. El volumen que ya existe en ese servidor está
# vacío, así que lo toma al recrearse el contenedor con esta imagen.
RUN mkdir /informes && chown sicot:sicot /informes

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
#
# stdout.encoding / stderr.encoding — sin esto los logs PIERDEN LOS ACENTOS.
# Desde el JDK 19 esas dos propiedades se derivan de la codificación nativa, y
# en Alpine, que no trae locales, `LANG` viene vacío: la JVM resuelve
# `native.encoding = ANSI_X3.4-1968` (ASCII) y cada tilde y cada eñe sale como
# «?». Se ve en la salida de la propia suite:
#     Extracci?n de 'acta.pdf' completada
#     El modelo no respondi? para 'notificacion.pdf'
# Todos los mensajes de SICOT están en español y están escritos uno por uno
# para que el técnico del Centro entienda qué pasó sin leer el código; servirlos
# mutilados tira a la basura justo ese trabajo. No se arregla con `ENV LANG`:
# musl ignora los locales que no tiene instalados, así que se fija la propiedad
# de la JVM, que es la que de verdad decide. `file.encoding` ya es UTF-8 por
# defecto desde el JDK 18 (JEP 400) y no hace falta tocarlo.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

# El healthcheck vive también aquí, no solo en docker-compose.yml: así la
# imagen se comporta igual cuando se despliega con otra herramienta
# (docker run, Swarm, Kubernetes) que no lea ese archivo.
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

# Forma "shell" para que $JAVA_OPTS se expanda; con la forma exec se pasaría
# como una sola cadena literal y la JVM la rechazaría.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
