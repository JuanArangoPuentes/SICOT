-- ═══════════════════════════════════════════════════════════════════════════
-- Motor de automatizaciones: cola de trabajo persistente y origen del registro.
--
-- EL PROBLEMA QUE RESUELVE
-- ------------------------
-- Hasta esta migración, nada en SICOT creaba nunca una alerta. La tabla
-- `alertas` existe desde V1 y admite diez tipos, pero el backend solo sabía
-- listarlas y marcarlas leídas: no había una sola inserción en todo el código.
-- La pantalla de alertas de un contrato real estaba vacía por construcción.
--
-- Lo único que avisaba de algo era el semáforo de cronograma, y se calcula en
-- el navegador (FR-010). Eso significa que un contrato en rojo solo está en
-- rojo mientras alguien lo mira: sin registro, sin fecha, sin historial.
--
-- Ver docs/decisiones/ADR-008 para por qué el motor vive dentro del backend y
-- no en n8n ni en un proceso aparte.
--
-- POR QUÉ UNA COLA EN LA BASE Y NO EN MEMORIA
-- -------------------------------------------
-- Una cola en memoria pierde todo el trabajo pendiente en cada reinicio, y lo
-- pierde en silencio. Con un solo host y un RPO de 24 h (ADR-002), un
-- despliegue a media tarde se llevaría por delante los correos y las alertas
-- que estuvieran esperando, y nadie se enteraría: no queda rastro de lo que se
-- perdió porque nunca llegó a existir en ningún sitio. La misma base que ya se
-- respalda a diario es el sitio correcto.
--
-- POR QUÉ `clave_idempotencia` ES UNIQUE Y NO OPCIONAL
-- ---------------------------------------------------
-- Las reglas de calendario se evalúan todos los días. Una regla que avisa «a
-- este contrato le quedan 30 días» crearía esa misma alerta cada mañana hasta
-- el vencimiento: treinta alertas idénticas para un solo hecho. Así es como
-- mueren en la práctica los sistemas de alertas — no se apagan, se vuelven
-- ruido y la gente deja de mirarlos.
--
-- La restricción UNIQUE convierte eso en un problema imposible en vez de un
-- problema que hay que acordarse de evitar: la segunda inserción con la misma
-- clave la rechaza la base, no la buena voluntad de quien escriba la regla.
-- La clave la compone la propia regla e identifica el HECHO, no el momento:
-- `vencimiento:contrato=7:umbral=30`.
--
-- POR QUÉ `ejecutar_en` Y NO UNA COLA FIFO A SECAS
-- ------------------------------------------------
-- Los reintentos necesitan espera exponencial. Un correo que falla porque el
-- servidor SMTP está caído volverá a fallar si se reintenta inmediatamente, y
-- reintentar en bucle contra un servicio caído es una forma eficaz de que ese
-- servicio siga caído. `ejecutar_en` es el momento a partir del cual la tarea
-- vuelve a ser elegible; el sondeo simplemente ignora las que aún no lo son.
--
-- POR QUÉ ESTA TABLA NO LLEVA `lock_version`
-- ------------------------------------------
-- V12 añadió bloqueo optimista a las siete tablas cuyas filas se modifican, y
-- dejó fuera `registros` y `alertas` explicando por qué. Esta queda fuera por
-- un motivo distinto: ya tiene su propio control de concurrencia, y es más
-- fuerte para este caso.
--
-- Un trabajador toma una tarea con un UPDATE condicionado al estado
-- (`SET estado='EN_PROCESO' WHERE id=? AND estado='PENDIENTE'`). Si otro
-- trabajador se le adelantó, ese UPDATE afecta cero filas y el segundo
-- trabajador simplemente pasa a la siguiente tarea. Es una toma atómica: no
-- hay ventana entre comprobar y reservar. Añadir encima una columna de versión
-- sería un segundo mecanismo resolviendo el mismo problema, y convertiría cada
-- reintento normal en un conflicto que alguien tendría que interpretar.
--
-- POR QUÉ `registros.origen`
-- --------------------------
-- `registros.usuario_id` ya era nulable, pero en la práctica nunca es nulo:
-- los trece puntos que escriben auditoría corren bajo una petición autenticada.
-- Cuando las automatizaciones empiecen a registrar acciones, su usuario será
-- nulo, y en la pantalla de auditoría eso se vería como un actor en blanco —
-- indistinguible de un fallo de la aplicación.
--
-- Con una columna explícita, «lo hizo el sistema» es un dato afirmado y no la
-- ausencia de otro. En un expediente de contratación pública esa diferencia
-- importa: quien lo revise necesita saber que ahí no falta información.
--
-- No se crea un usuario ficticio «Sistema» en `usuarios`: una fila con
-- credenciales que nadie usa es una cuenta que alguien puede acabar usando, y
-- ensuciaría todos los listados de administración.
-- ═══════════════════════════════════════════════════════════════════════════

-- ── Cola de tareas ─────────────────────────────────────────────────────────

CREATE TABLE tareas_automatizadas (
    id                  BIGSERIAL PRIMARY KEY,
    regla               VARCHAR(80)  NOT NULL,
    tipo                VARCHAR(30)  NOT NULL,
    contrato_id         BIGINT       REFERENCES contratos (id) ON DELETE CASCADE,
    clave_idempotencia  VARCHAR(200) NOT NULL,
    payload             TEXT         NOT NULL,
    estado              VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    intentos            INT          NOT NULL DEFAULT 0,
    ejecutar_en         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ultimo_error        TEXT,
    fecha_creacion      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_tareas_automatizadas_tipo CHECK (
        tipo IN ('CREAR_ALERTA', 'ENVIAR_CORREO', 'REDACTAR_RESUMEN_IA')),
    CONSTRAINT ck_tareas_automatizadas_estado CHECK (
        estado IN ('PENDIENTE', 'EN_PROCESO', 'COMPLETADA', 'FALLIDA', 'DESCARTADA')),
    CONSTRAINT ck_tareas_automatizadas_intentos CHECK (intentos >= 0),
    CONSTRAINT uq_tareas_automatizadas_idempotencia UNIQUE (clave_idempotencia)
);

-- El índice que sostiene el sondeo: «las pendientes que ya tocan, más antigua
-- primero». Parcial a propósito — una cola sana tiene casi todas las filas en
-- COMPLETADA, y esas no aportan nada a la consulta que se ejecuta cada minuto.
CREATE INDEX idx_tareas_automatizadas_pendientes
    ON tareas_automatizadas (ejecutar_en)
    WHERE estado = 'PENDIENTE';

-- Para la pantalla de operación: «qué pasó con las automatizaciones de este
-- contrato» y «qué falló últimamente», sin recorrer la tabla entera.
CREATE INDEX idx_tareas_automatizadas_contrato ON tareas_automatizadas (contrato_id);
CREATE INDEX idx_tareas_automatizadas_estado_fecha
    ON tareas_automatizadas (estado, fecha_creacion DESC);

-- ── Origen del registro de auditoría ───────────────────────────────────────

ALTER TABLE registros
    ADD COLUMN IF NOT EXISTS origen VARCHAR(10) NOT NULL DEFAULT 'USUARIO';

-- Las filas que ya existían las escribió una persona autenticada: el DEFAULT
-- las deja correctamente marcadas sin necesidad de un UPDATE de relleno.
ALTER TABLE registros
    ADD CONSTRAINT ck_registros_origen CHECK (origen IN ('USUARIO', 'SISTEMA'));
