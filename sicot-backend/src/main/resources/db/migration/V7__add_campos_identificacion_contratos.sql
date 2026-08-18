-- ═══════════════════════════════════════════════════════════════════════════
-- SICOT — V7: campos de identificación real del contrato
-- Estructura tomada de los formatos oficiales del SENA (Acta de Inicio
-- GCCON-F-018, Informe de Supervisión GCCON-F-031, Informe Final GCCON-F-030):
-- todo contrato real identifica tipo, contratista, NIT/CC, representante
-- legal, lugar de ejecución y registro presupuestal. Nullable — un contrato
-- en BORRADOR no siempre tiene todo esto todavía (igual que fecha_inicio/fin).
-- Fuera de alcance a propósito: garantías/pólizas, avance financiero por
-- informe, generación de documentos — quedan para una fase posterior.
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE contratos
    ADD COLUMN tipo_contrato               VARCHAR(100),
    ADD COLUMN contratista                 VARCHAR(255),
    ADD COLUMN contratista_nit             VARCHAR(30),
    ADD COLUMN representante_legal         VARCHAR(255),
    ADD COLUMN lugar_ejecucion             VARCHAR(255),
    ADD COLUMN numero_registro_presupuestal VARCHAR(50),
    ADD COLUMN fecha_registro_presupuestal DATE,
    ADD COLUMN centro_costo                VARCHAR(100);
