-- ═══════════════════════════════════════════════════════════════════════════
-- SICOT — V3: Eliminar datos transaccionales de desarrollo
-- Conserva: estructura de tablas, constraints, índices, enums, secuencias
-- Conserva: usuarios de desarrollo (creados por DataInitializer)
-- Elimina: contratos, etapas, subetapas, documentos, alertas, registros
-- ═══════════════════════════════════════════════════════════════════════════

-- Orden de eliminación respeta foreign keys (hijos primero)
DELETE FROM registros;
DELETE FROM alertas;
DELETE FROM documentos;
DELETE FROM subetapas;
DELETE FROM etapas;
DELETE FROM contratos;
