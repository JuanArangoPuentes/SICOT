-- ═══════════════════════════════════════════════════════════════════════════
-- Fecha y lugar de captura de las fotos de evidencia (MDL-205).
--
-- La subetapa 3.2 del GCCON-P-010 pide evidencia fotográfica georreferenciada
-- de la entrega en bodega. Desde el 22 de septiembre de 2026 la foto entra al
-- expediente tal como sale de la cámara, justamente para no borrar la fecha y
-- la ubicación que el teléfono escribe dentro (EXIF). Pero nada las leía: el
-- «cuándo y dónde se recibió», que es lo que convierte la foto en evidencia,
-- no aparecía en ninguna parte.
--
-- Las tres columnas se rellenan al cargar, leyendo el EXIF del archivo. Son
-- NULL cuando la foto no trae el dato, y así se quedan: nunca se completan con
-- la fecha de carga ni con una ubicación aproximada, porque un dato de
-- evidencia inventado es peor que uno ausente. Por lo mismo, los documentos
-- cargados antes de esta migración no se rellenan aquí.
--
-- captura_fecha es un instante (timestamptz), como el resto de fechas del
-- esquema. Si el EXIF no trae desfase horario, la aplicación interpreta la
-- hora en la zona del Centro (sicot.zona-horaria), que es la del teléfono que
-- tomó la foto.
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE documentos
    ADD COLUMN captura_fecha    timestamptz,
    ADD COLUMN captura_latitud  double precision,
    ADD COLUMN captura_longitud double precision;

-- Una coordenada sin la otra no es una ubicación, y fuera de rango es un EXIF
-- corrupto. La base lo impide aunque la aplicación ya lo filtre, como con el
-- resto de invariantes del esquema.
ALTER TABLE documentos
    ADD CONSTRAINT ck_documentos_captura_ubicacion CHECK (
        (captura_latitud IS NULL) = (captura_longitud IS NULL)
        AND (captura_latitud IS NULL OR captura_latitud BETWEEN -90 AND 90)
        AND (captura_longitud IS NULL OR captura_longitud BETWEEN -180 AND 180)
    );
