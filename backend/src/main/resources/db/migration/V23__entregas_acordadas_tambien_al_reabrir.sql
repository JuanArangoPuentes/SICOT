-- ═══════════════════════════════════════════════════════════════════════════
-- La exigencia del 2.4 también para los que se cerraron antes de la V22.
--
-- POR QUÉ
-- -------
-- La V22 marcó con ENTREGAS_ACORDADAS los 2.4 del proceso real que no estaban
-- cerrados, y dejó sin marca los que ya lo estaban. Pero la marca no reabre
-- nada: EtapaService solo la mira en el próximo cierre. Un 2.4 cerrado antes del
-- 22-10-2026 y reabierto como corrección (lo permite TransicionesDeEstado y queda
-- como SUBETAPA_REVERTIDA) se volvía a cerrar sin una sola fecha acordada,
-- mientras el panel le mostraba «Fechas de entrega acordadas». Revisión del
-- 23-10-2026.
--
-- Marcar los cerrados no los toca: siguen cerrados, y solo un cierre nuevo pide
-- las fechas. Va en una migración aparte porque la V22 ya está aplicada en la
-- base de desarrollo y cambiarla rompería su suma de verificación.
-- ═══════════════════════════════════════════════════════════════════════════
UPDATE subetapas
   SET documentos_requeridos = 'ENTREGAS_ACORDADAS'
 WHERE codigo = '2.4'
   AND nombre = 'Contacto con el contratista y acuerdo de entregas'
   AND documentos_requeridos IS NULL;
