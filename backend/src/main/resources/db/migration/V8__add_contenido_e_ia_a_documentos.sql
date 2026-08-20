-- ═══════════════════════════════════════════════════════════════════════════
-- SICOT — V8: contenido real de documentos + trazabilidad de generación/firma
-- por IA local (Ollama). Reemplaza el mock del frontend (barra de progreso
-- falsa, datos "—" precargados, firma aleatoria en el navegador) por
-- persistencia real, siguiendo el mismo patrón BYTEA que V4
-- (formatos_documentales): el archivo se guarda en la propia base de datos.
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE documentos
    ADD COLUMN content_type    VARCHAR(100),
    ADD COLUMN contenido       BYTEA,
    ADD COLUMN tamanio_bytes   BIGINT,
    ADD COLUMN generado_por_ia BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN firma_id        VARCHAR(50),
    ADD COLUMN fecha_firma     TIMESTAMPTZ,
    ADD COLUMN subido_por_id   BIGINT REFERENCES usuarios (id) ON DELETE SET NULL;

CREATE INDEX idx_documentos_subido_por ON documentos (subido_por_id);
