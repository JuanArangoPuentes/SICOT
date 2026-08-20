-- ═══════════════════════════════════════════════════════════════════════════
-- SICOT — V4: Catálogo de formatos documentales oficiales
-- Formatos institucionales (GCCON-*, GIL-*, ESUCON, etc.) que el Administrador
-- carga desde el Panel de Administrador y que sirven de referencia real para
-- el proceso de supervisión. El archivo se guarda en la propia base de datos
-- (BYTEA) para no depender de una ruta de disco distinta por entorno.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE formatos_documentales (
    id                  BIGSERIAL PRIMARY KEY,
    codigo              VARCHAR(50)   NOT NULL UNIQUE,
    nombre              VARCHAR(255)  NOT NULL,
    version             VARCHAR(20)   NOT NULL DEFAULT 'v1',
    tipo_archivo        VARCHAR(20)   NOT NULL,
    nombre_archivo      VARCHAR(255)  NOT NULL,
    content_type        VARCHAR(100)  NOT NULL,
    contenido           BYTEA         NOT NULL,
    tamanio_bytes        BIGINT        NOT NULL,
    estado              VARCHAR(20)   NOT NULL DEFAULT 'VIGENTE',
    subido_por_id       BIGINT REFERENCES usuarios (id) ON DELETE SET NULL,
    fecha_creacion      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_formatos_tipo_archivo CHECK (tipo_archivo IN ('PDF', 'DOCX', 'XLSX', 'IMAGEN', 'OTRO')),
    CONSTRAINT ck_formatos_estado CHECK (estado IN ('VIGENTE', 'OBSOLETO'))
);

CREATE INDEX idx_formatos_subido_por ON formatos_documentales (subido_por_id);
