-- ═══════════════════════════════════════════════════════════════════════════
-- Índices compuestos para las dos tablas que crecen sin techo.
--
-- `alertas` y `registros` son las únicas tablas del sistema cuyo tamaño no está
-- acotado por el negocio: cada acción sobre un contrato escribe un registro de
-- auditoría, y las alertas se acumulan durante toda la vida del contrato. El
-- resto (contratos, etapas, subetapas, usuarios, formatos) está limitado por lo
-- que el centro alcanza a tramitar.
--
-- Las dos consultas por contrato — RegistroService.listarPorContrato y
-- AlertaService.listarPorContrato — filtran por contrato_id y ordenan por fecha
-- descendente, y ninguna de las dos tiene tope de filas (a diferencia de los
-- listados globales, que sí paginan). Con un índice solo sobre contrato_id,
-- PostgreSQL trae todas las filas del contrato y las ordena en memoria en cada
-- petición; el costo crece con la antigüedad del contrato, justo en la pantalla
-- que más se consulta cuando un contrato lleva tiempo abierto.
--
-- El índice compuesto (contrato_id, fecha DESC) devuelve las filas ya
-- ordenadas. Además reemplaza por completo al índice de una sola columna: un
-- índice cuyo prefijo es contrato_id sirve igual para buscar por contrato_id
-- solo, y también para el ON DELETE CASCADE de la clave foránea. Por eso los
-- antiguos se eliminan en vez de dejarlos: dos índices redundantes encarecen
-- cada INSERT sin acelerar ninguna lectura.
--
-- Los índices por fecha global de V9 (idx_alertas_fecha_creacion,
-- idx_registros_fecha) NO se tocan: sirven a los listados globales, que ordenan
-- por fecha sin filtrar por contrato, y esos no los cubre el compuesto.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE INDEX IF NOT EXISTS idx_registros_contrato_fecha
    ON registros (contrato_id, fecha DESC);
DROP INDEX IF EXISTS idx_registros_contrato;

CREATE INDEX IF NOT EXISTS idx_alertas_contrato_fecha
    ON alertas (contrato_id, fecha_creacion DESC);
DROP INDEX IF EXISTS idx_alertas_contrato;
