-- ═══════════════════════════════════════════════════════════════════════════
-- Cerrar sesión cierra la sesión.
--
-- POR QUÉ EXISTE ESTA TABLA
-- -------------------------
-- Hasta ahora «cerrar sesión» era una operación SOLO DEL CLIENTE: la
-- aplicación borraba el token de localStorage y lo olvidaba. El token seguía
-- siendo válido en el servidor hasta su expiración, OCHO HORAS después
-- (JWT_EXPIRATION_MS). Quien hubiera copiado ese token antes —de un equipo
-- compartido del Centro, de un portátil que cambió de manos— seguía entrando
-- con él, y la persona que acababa de pulsar «Cerrar sesión» tenía todos los
-- motivos para creer que ya no.
--
-- La única revocación que existía era `usuarios.version_credenciales` (V19),
-- que invalida TODAS las sesiones de la cuenta a la vez. Sirve para lo que fue
-- pensada —cambiar la contraseña, desactivar la cuenta— y no para esto: un
-- supervisor usa a la vez la aplicación de escritorio y el APK, y cerrar
-- sesión en el portátil del Centro no puede echarlo del teléfono que lleva a
-- bodega. Hacía falta poder invalidar UN token, no una cuenta.
--
-- CÓMO
-- ----
-- Cada token lleva desde hoy un identificador propio (el `jti` de la RFC 7519).
-- Cerrar sesión anota ese identificador aquí, y el filtro JWT rechaza cualquier
-- token cuyo `jti` esté en la tabla. Es una lista de NEGACIÓN, no de sesiones
-- válidas: así la autenticación sigue sin estado y lo único que se guarda son
-- las excepciones, que son pocas y se borran solas.
--
-- `expira_en` es la expiración del propio token. Pasada esa fecha la fila ya no
-- protege de nada —el token no valdría igual— y se puede borrar: de eso se
-- encarga la limpieza periódica de SesionesCerradas. Sin esa columna habría que
-- conservar las filas para siempre o adivinar cuándo sobran.
--
-- LO QUE ESTO NO CUBRE, DICHO AQUÍ Y NO DESPUÉS
-- ---------------------------------------------
-- Un token emitido ANTES de esta migración no tiene `jti`, así que no se puede
-- anotar individualmente. Para esos, cerrar sesión recurre a
-- `revocarSesiones()`: cierra toda la cuenta, que es más de lo pedido pero
-- nunca menos, y el caso desaparece en cuanto todos vuelven a entrar (ocho
-- horas como máximo). Lo alternativo era rechazar los tokens sin `jti` al
-- desplegar, es decir cerrarle la sesión a todo el Centro a mitad de la tarde.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE sesiones_cerradas (
    -- El `jti` del token. Es la clave primaria porque cerrar sesión dos veces
    -- con el mismo token tiene que ser idempotente: el segundo intento (un
    -- doble clic, un reintento de la cola) no puede fallar con un error.
    jti           VARCHAR(64)  PRIMARY KEY,

    -- A quién pertenecía. No hace falta para la comprobación y se guarda igual:
    -- sin esto, una revisión de seguridad no puede responder «¿de quién era la
    -- sesión que se cerró a las 15:40?». ON DELETE CASCADE porque la fila no
    -- tiene ningún valor sin la cuenta.
    usuario_id    BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,

    cerrada_en    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Cuándo el token habría caducado por su cuenta. Ver arriba.
    expira_en     TIMESTAMPTZ  NOT NULL
);

-- La limpieza borra por `expira_en`; sin índice sería un recorrido completo de
-- la tabla en cada pasada. La tabla es pequeña hoy, y un índice en la columna
-- por la que se borra es lo que impide que deje de serlo sin que nadie lo note.
CREATE INDEX idx_sesiones_cerradas_expira ON sesiones_cerradas (expira_en);

-- Para la pregunta «¿qué sesiones de esta cuenta se cerraron?», que es la que
-- se hace en una revisión.
CREATE INDEX idx_sesiones_cerradas_usuario ON sesiones_cerradas (usuario_id);
