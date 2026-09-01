-- ═══════════════════════════════════════════════════════════════════════════
-- Reconciliación del esquema con V1.
--
-- POR QUÉ EXISTE ESTA MIGRACIÓN
-- ------------------------------
-- Las migraciones V1–V8 originales se consolidaron en la V1 actual (ago 2026).
-- La consolidación no fue una copia literal: la nueva V1 agregó siete objetos
-- de integridad que las originales no tenían. En una base creada desde cero eso
-- no se nota, porque V1 los crea. Pero en las bases que YA existían, el
-- historial de Flyway se reparó para que la V1 nueva figurara como aplicada, y
-- esos siete objetos nunca llegaron a ejecutarse.
--
-- Resultado: dos bases del mismo proyecto con esquemas distintos. Una base de
-- desarrollo antigua acepta `valor = -1` y dos firmas activas por usuario; una
-- base recién creada (o la de producción el día que se despliegue) las rechaza.
-- Los defectos que dependen de eso solo aparecen en la base nueva — es decir,
-- en producción.
--
-- Esta migración cierra esa brecha. Cada objeto se crea SOLO si falta, así que
-- es un no-op en las bases creadas desde cero con la V1 actual y es la que
-- pone al día a las bases antiguas. Después de aplicarla, todas las bases del
-- proyecto tienen exactamente el mismo esquema, sin importar cuándo se crearon.
--
-- Los datos existentes se verificaron contra las siete restricciones antes de
-- escribir esta migración: cero filas violan alguna. Por eso se validan de
-- inmediato en vez de quedar NOT VALID.
--
-- NOTA SOBRE current_schema(): cada comprobación de existencia filtra por el
-- esquema actual. No es un detalle de estilo. pg_constraint y pg_indexes son
-- catálogos de TODA la base, no del esquema: sin ese filtro, un objeto con el
-- mismo nombre en cualquier otro esquema —por ejemplo el esquema desechable que
-- crea EsquemaPostgreSqlIntegrationTest— hace que la comprobación dé positivo y
-- la migración se salte la creación en el esquema que sí la necesitaba,
-- registrándose como aplicada con éxito. Esta migración se escribió primero sin
-- el filtro y ocurrió exactamente eso.
-- ═══════════════════════════════════════════════════════════════════════════

-- ── contratos ──────────────────────────────────────────────────────────────
-- Un contrato con valor negativo no es un dato del dominio, es un error de
-- captura o de un cliente mal escrito. La API ya lo valida (@PositiveOrZero),
-- pero la API no es el único camino a la tabla.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                    WHERE n.nspname = current_schema() AND c.conname = 'ck_contratos_valor') THEN
        ALTER TABLE contratos ADD CONSTRAINT ck_contratos_valor CHECK (valor >= 0);
    END IF;
END $$;

-- ContratoService.crear/actualizar rechaza fecha_fin < fecha_inicio. La misma
-- regla, en la base, para que ningún otro camino de escritura la salte.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                    WHERE n.nspname = current_schema() AND c.conname = 'ck_contratos_fechas') THEN
        ALTER TABLE contratos ADD CONSTRAINT ck_contratos_fechas
            CHECK (fecha_fin IS NULL OR fecha_inicio IS NULL OR fecha_fin >= fecha_inicio);
    END IF;
END $$;

-- ── etapas ─────────────────────────────────────────────────────────────────
-- Las etapas del GCCON-P-010 se numeran 1..6. Un 0 o un negativo rompería el
-- orden del panel de Supervisor sin que nada avise.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                    WHERE n.nspname = current_schema() AND c.conname = 'ck_etapas_numero') THEN
        ALTER TABLE etapas ADD CONSTRAINT ck_etapas_numero CHECK (numero > 0);
    END IF;
END $$;

-- ── documentos ─────────────────────────────────────────────────────────────
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                    WHERE n.nspname = current_schema() AND c.conname = 'ck_documentos_tamanio') THEN
        ALTER TABLE documentos ADD CONSTRAINT ck_documentos_tamanio
            CHECK (tamanio_bytes IS NULL OR tamanio_bytes >= 0);
    END IF;
END $$;

-- ── formatos_documentales ──────────────────────────────────────────────────
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                    WHERE n.nspname = current_schema() AND c.conname = 'ck_formatos_tamanio') THEN
        ALTER TABLE formatos_documentales ADD CONSTRAINT ck_formatos_tamanio
            CHECK (tamanio_bytes >= 0);
    END IF;
END $$;

-- ── firmas_electronicas ────────────────────────────────────────────────────
-- El identificador de firma es la evidencia que queda copiada en
-- documentos.firma_id cuando alguien firma. Si dos filas pudieran compartirlo,
-- un documento firmado dejaría de identificar a una sola persona y la
-- trazabilidad de la firma —el punto entero de la tabla— se perdería.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                    WHERE n.nspname = current_schema() AND c.conname = 'uq_firmas_electronicas_firma_id') THEN
        ALTER TABLE firmas_electronicas ADD CONSTRAINT uq_firmas_electronicas_firma_id UNIQUE (firma_id);
    END IF;
END $$;

-- Un usuario tiene como máximo UNA firma activa. Es la regla en la que se apoya
-- DocumentoService.firmar, que resuelve la firma del usuario con
-- findFirstByUsuarioIdAndActivaTrue: con dos activas, "primera" depende del
-- orden que devuelva el motor y el documento quedaría firmado con una firma
-- arbitraria. Índice parcial y no UNIQUE simple porque las firmas revocadas
-- (activa = FALSE) se conservan como histórico y sí pueden ser varias.
-- Se comprueba el catálogo en vez de usar CREATE INDEX IF NOT EXISTS: esa forma
-- funciona igual, pero cuando el índice ya está (toda base creada desde cero con
-- la V1 actual) PostgreSQL emite un NOTICE que Flyway registra como WARNING. Un
-- despliegue nuevo mostraría una advertencia en el arranque por algo que está
-- perfectamente bien, y un log de producción que avisa de no-problemas enseña a
-- ignorar los avisos que sí importan.
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
         WHERE schemaname = current_schema() AND indexname = 'uq_firma_activa_por_usuario'
    ) THEN
        CREATE UNIQUE INDEX uq_firma_activa_por_usuario
            ON firmas_electronicas (usuario_id) WHERE activa = TRUE;
    END IF;
END $$;
