-- ═══════════════════════════════════════════════════════════════════════════
-- SICOT — V6: firmas electrónicas asignadas a cuentas reales
-- Reemplaza el mock del frontend (useState + setTimeout) por persistencia
-- real. Se permiten varias filas por usuario para conservar el histórico de
-- revocaciones/reasignaciones (auditable), en vez de una sola columna en
-- usuarios que perdería ese historial.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE firmas_electronicas (
    id                  BIGSERIAL PRIMARY KEY,
    usuario_id          BIGINT NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    firma_id            VARCHAR(50) NOT NULL,
    activa              BOOLEAN NOT NULL DEFAULT TRUE,
    asignado_por_id     BIGINT REFERENCES usuarios (id) ON DELETE SET NULL,
    fecha_asignacion    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_firmas_electronicas_usuario ON firmas_electronicas (usuario_id);
