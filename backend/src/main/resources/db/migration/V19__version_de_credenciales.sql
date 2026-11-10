-- ═══════════════════════════════════════════════════════════════════════════
-- Versión de credenciales: cambiar la contraseña cierra las sesiones abiertas.
--
-- Un JWT de SICOT vale hasta 8 horas y el filtro solo comprobaba la firma, la
-- expiración y que la cuenta siguiera activa. Restablecer la contraseña de una
-- cuenta que se sospecha comprometida no cortaba nada: quien tuviera el token
-- seguía leyendo el contrato, cargando documentos y cambiando subetapas hasta
-- que venciera. La única salida era desactivar la cuenta.
--
-- version_credenciales viaja en cada token emitido y el filtro la compara con
-- la de la base. La aplicación la incrementa al cambiar la contraseña y al
-- desactivar la cuenta, y con eso todos los tokens anteriores dejan de valer
-- en la siguiente petición. Es un contador y no una fecha porque el instante de
-- emisión del token tiene precisión de segundos: un token emitido en el mismo
-- segundo del cambio quedaría en un empate que una fecha no sabe resolver.
--
-- Arranca en 0 para todas las cuentas existentes, que es el valor que el
-- filtro supone en los tokens emitidos antes de esta migración: desplegarla no
-- cierra la sesión de nadie.
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE usuarios
    ADD COLUMN version_credenciales BIGINT NOT NULL DEFAULT 0;

ALTER TABLE usuarios
    ADD CONSTRAINT ck_usuarios_version_credenciales CHECK (version_credenciales >= 0);
