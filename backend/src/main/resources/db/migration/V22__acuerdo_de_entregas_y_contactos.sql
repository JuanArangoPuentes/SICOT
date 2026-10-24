-- ═══════════════════════════════════════════════════════════════════════════
-- Lo que la lista de chequeo de Alex pide recordar y SICOT no guardaba: las
-- fechas de entrega acordadas con el contratista, los profesionales de SST y
-- ambiental a los que se les avisa, y el teléfono del contratista.
--
-- POR QUÉ
-- -------
-- La hoja de Alex Zapata (docs/producto/LISTA_CHEQUEO_ALEX.md) dice en el
-- paso 5: «se establecen fechas de entrega o la prestación del servicio, las
-- fechas se definen de común acuerdo con el contratista respetando los tiempos
-- de inicio y fin del contrato». SICOT armaba el correo que propone acordarlas,
-- pero el acuerdo no tenía dónde quedar: el paso 2.4 se cerraba sin una sola
-- fecha, y el correo a SST y ambiental prometía «les compartiré las fechas de
-- entrega en cuanto queden acordadas» sin que hubiera de dónde sacarlas.
--
-- El paso 3 de la hoja pide identificar a los profesionales de SST y
-- ambiental (están en el estudio previo) y escribirles. El borrador salía sin
-- destinatarios y en cada correo había que volver a buscarlos.
--
-- Y el paso 5 dice que en el acta «debe reposar un correo de contacto +
-- número de teléfono». El acta del Lote 4 no trae teléfono; el registro de
-- proveedores del SECOP II (datos abiertos, qmzu-gj57) sí: 4489948 para
-- Suministros Maybe. Se guarda con el contrato como los demás datos.
-- ═══════════════════════════════════════════════════════════════════════════

-- ─────────────────────────── Entregas acordadas ───────────────────────────
CREATE TABLE entregas_acordadas (
    id              BIGSERIAL PRIMARY KEY,
    contrato_id     BIGINT NOT NULL REFERENCES contratos (id) ON DELETE CASCADE,

    -- El orden de la entrega en el contrato («la entrega 2»), que es como la
    -- nombran el supervisor y el contratista. Lo asigna SICOT por fecha al
    -- guardar: no es un dato que se escriba a mano.
    numero          INTEGER NOT NULL CHECK (numero > 0),

    -- El día acordado. Que caiga dentro del plazo del contrato lo exige el
    -- servicio y no la base: el plazo vive en otra tabla y una prórroga lo
    -- mueve, y una restricción entre tablas no se puede declarar como CHECK.
    fecha           DATE NOT NULL,

    -- Qué se entrega ese día, o qué servicio se presta: «entrega total»,
    -- «ítems 1 a 20». TEXT, como la descripción de los ítems del pedido.
    descripcion     TEXT NOT NULL,

    CONSTRAINT uk_entrega_acordada_numero UNIQUE (contrato_id, numero)
);

-- Se leen siempre por contrato y en orden de fecha: para mostrarlas en el paso
-- y para que el motor de automatizaciones recuerde las próximas.
CREATE INDEX idx_entregas_acordadas_contrato ON entregas_acordadas (contrato_id, fecha);

-- ───────────────────── Profesionales de SST y ambiental ─────────────────────
CREATE TABLE contactos_del_contrato (
    id              BIGSERIAL PRIMARY KEY,
    contrato_id     BIGINT NOT NULL REFERENCES contratos (id) ON DELETE CASCADE,

    -- A quién representa: hoy solo los dos que nombra el paso 3 de la hoja.
    rol             VARCHAR(20) NOT NULL,

    -- El nombre puede no estar a mano (el estudio previo a veces solo trae el
    -- correo del área); el correo sí, porque es para lo que se guarda.
    nombre          VARCHAR(255),
    correo          VARCHAR(255) NOT NULL,

    CONSTRAINT ck_contactos_del_contrato_rol CHECK (rol IN ('SST', 'AMBIENTAL')),
    -- La misma persona dos veces con el mismo rol solo duplicaría el
    -- destinatario del correo.
    CONSTRAINT uk_contacto_del_contrato UNIQUE (contrato_id, rol, correo)
);

CREATE INDEX idx_contactos_del_contrato_contrato ON contactos_del_contrato (contrato_id);

-- ─────────────────────────── Teléfono del contratista ───────────────────────
-- Texto y no número: los teléfonos fijos se publican con o sin indicativo
-- («4489948», «604 4489948»), los celulares con espacios o con +57, y un cero
-- inicial no puede perderse.
ALTER TABLE contratos ADD COLUMN telefono_contratista VARCHAR(40);

-- ───────────────────── El paso 2.4 espera el acuerdo de entregas ─────────────
-- Como el paso del acta (V20), el 2.4 ya no se cierra con un clic: exige que
-- haya al menos una fecha de entrega acordada. Lo hace cumplir EtapaService.
ALTER TABLE subetapas DROP CONSTRAINT ck_subetapas_documentos_requeridos;
ALTER TABLE subetapas ADD CONSTRAINT ck_subetapas_documentos_requeridos
    CHECK (documentos_requeridos IS NULL
           OR documentos_requeridos IN ('ACTA_FIRMADA_Y_ENVIO', 'ENTREGAS_ACORDADAS'));

-- Los contratos que ya siguen el proceso real y no han cerrado el 2.4 pasan a
-- exigirlo también. Se reconocen por el nombre del sub-paso, igual que en el
-- panel: los contratos anteriores al 15-10-2026 tienen otro 2.4 con el mismo
-- código, y a esos no se les toca. Los que ya lo cerraron quedan como están:
-- no se reabre un paso cerrado por una regla que no existía cuando se cerró.
UPDATE subetapas
   SET documentos_requeridos = 'ENTREGAS_ACORDADAS'
 WHERE codigo = '2.4'
   AND nombre = 'Contacto con el contratista y acuerdo de entregas'
   AND estado <> 'COMPLETADA'
   AND documentos_requeridos IS NULL;
