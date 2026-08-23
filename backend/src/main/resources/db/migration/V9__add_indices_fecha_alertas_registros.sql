-- Los listados globales (todas las alertas, todos los registros de auditoría)
-- ordenan por fecha descendente pero no tenían índice sobre esa columna: cada
-- consulta forzaba un sort completo de tabla. Se agregan aquí, junto con la
-- paginación aplicada en AlertaService/RegistroService (ver commit), porque
-- ambas tablas crecen sin límite con el uso normal del sistema.
CREATE INDEX idx_alertas_fecha_creacion ON alertas (fecha_creacion DESC);
CREATE INDEX idx_registros_fecha ON registros (fecha DESC);
