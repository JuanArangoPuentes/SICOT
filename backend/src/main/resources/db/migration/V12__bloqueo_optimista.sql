-- ═══════════════════════════════════════════════════════════════════════════
-- Bloqueo optimista (columna de versión gestionada por Hibernate).
--
-- EL PROBLEMA QUE RESUELVE
-- ------------------------
-- Cada operación de escritura del backend es un leer-modificar-escribir dentro
-- de una transacción: carga la entidad, la modifica y la guarda. Cuando dos
-- peticiones sobre la misma fila se solapan, ambas leen el mismo estado inicial
-- y la segunda en confirmar pisa a la primera. La modificación de la primera
-- desaparece sin error y sin rastro: la auditoría registra dos operaciones
-- exitosas y el dato final refleja solo una. En un sistema de contratación
-- pública eso no es una molestia de concurrencia, es un dato oficial alterado
-- sin evidencia de qué se perdió.
--
-- El caso más claro está en FormatoDocumentalService.subir, que calcula la
-- versión siguiente del formato a partir de la actual (v1 → v2). Dos cargas
-- solapadas del mismo código leen "v1", ambas escriben "v2", y uno de los dos
-- archivos queda inalcanzable.
--
-- CÓMO FUNCIONA
-- -------------
-- Hibernate incluye `lock_version` en el WHERE de cada UPDATE y la incrementa.
-- Si otra transacción ya la movió, el UPDATE afecta 0 filas e Hibernate lanza
-- OptimisticLockingFailureException, que GlobalExceptionHandler traduce a HTTP
-- 409 con un mensaje que le dice al usuario que recargue. La escritura perdida
-- pasa de ser silenciosa a ser un error visible y reintentable.
--
-- ALCANCE (lo que esto NO cubre todavía)
-- --------------------------------------
-- Protege transacciones que se solapan en el servidor. NO protege el caso de
-- "abrí el formulario hace cinco minutos, alguien más guardó, y ahora guardo
-- yo": esa segunda petición vuelve a leer la fila, ve la versión ya
-- actualizada, y sobrescribe sin conflicto. Para cerrar también ese escenario
-- el cliente tendría que devolver la versión que leyó, lo que implica exponer
-- `lock_version` en los DTO de respuesta y exigirla en los de actualización.
-- Esta migración es el requisito previo de eso, no su reemplazo.
--
-- POR QUÉ `lock_version` Y NO `version`
-- -------------------------------------
-- `formatos_documentales` ya tiene una columna `version` VARCHAR con otro
-- significado por completo (la versión del formato institucional: v1, v2, …).
-- Usar el mismo nombre obligaría a que esa tabla fuera la excepción. Un solo
-- nombre en las siete tablas es más fácil de leer y de auditar.
--
-- QUÉ TABLAS NO LA LLEVAN
-- -----------------------
-- `registros` es un histórico de solo-inserción: sus filas nunca se modifican,
-- así que no hay actualización que pueda perderse. `alertas` solo cambia por
-- `leida = true`, una operación idempotente donde la última escritura gana es
-- exactamente el comportamiento correcto. Ponerles versión sería costo de
-- escritura sin ninguna garantía a cambio.
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE usuarios              ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE contratos             ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE etapas                ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE subetapas             ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE documentos            ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE formatos_documentales ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE firmas_electronicas   ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;
