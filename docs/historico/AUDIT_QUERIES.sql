-- ═══════════════════════════════════════════════════════════════════════════
-- SICOT — AUDITORÍA FASE 2 — Queries de inventario
-- ═══════════════════════════════════════════════════════════════════════════

-- 1. DOCUMENTOS
SELECT 'DOCUMENTOS' as "TABLA";
SELECT d.id, d.contrato_id, d.subetapa_id, d.nombre, d.tipo, d.estado, d.fecha_subida
FROM documentos d
ORDER BY d.contrato_id, d.id;

-- 2. ALERTAS
SELECT 'ALERTAS' as "TABLA";
SELECT a.id, a.contrato_id, a.tipo, a.prioridad, a.mensaje, a.leida, a.fecha_creacion
FROM alertas a
ORDER BY a.contrato_id, a.id;

-- 3. REGISTROS
SELECT 'REGISTROS' as "TABLA";
SELECT r.id, r.contrato_id, r.usuario_id, r.accion, r.descripcion, r.fecha
FROM registros r
ORDER BY r.contrato_id, r.id;

-- 4. FLYWAY MIGRATIONS
SELECT 'FLYWAY_SCHEMA_HISTORY' as "TABLA";
SELECT installed_rank, version, description, script, success, installed_by, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;

-- 5. COUNT SUMMARY
SELECT 'COUNTS' as "TABLA";
SELECT
  (SELECT COUNT(*) FROM usuarios) as usuarios,
  (SELECT COUNT(*) FROM contratos) as contratos,
  (SELECT COUNT(*) FROM etapas) as etapas,
  (SELECT COUNT(*) FROM subetapas) as subetapas,
  (SELECT COUNT(*) FROM documentos) as documentos,
  (SELECT COUNT(*) FROM alertas) as alertas,
  (SELECT COUNT(*) FROM registros) as registros;
