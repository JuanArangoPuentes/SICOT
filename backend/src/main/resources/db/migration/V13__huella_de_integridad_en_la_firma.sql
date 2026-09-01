-- ═══════════════════════════════════════════════════════════════════════════
-- Huella de integridad del documento firmado.
--
-- EL PROBLEMA QUE RESUELVE
-- ------------------------
-- Hasta ahora "firmar" consistía en escribir sobre la fila un identificador
-- aleatorio (firma_id, del estilo FIRMA-A1B2C3D4), la fecha, y marcar el
-- documento como APROBADO. Nada de eso estaba ligado a los BYTES del
-- documento: si alguien modificaba `contenido` después de la firma —por
-- acceso directo a la base, por un error de la aplicación, por una
-- restauración mal hecha— el documento seguía apareciendo firmado y válido, y
-- no quedaba ninguna evidencia de la alteración.
--
-- En contratación pública ese es exactamente el hecho que una firma debe
-- hacer imposible. Un documento oficial alterado sin rastro no es una
-- molestia técnica: es prueba destruida.
--
-- CÓMO FUNCIONA
-- -------------
-- Al firmar se calcula SHA-256 sobre el contenido exacto que se está firmando
-- y se guarda aquí en hexadecimal (64 caracteres). Al descargar o al pedir la
-- verificación, el backend vuelve a calcular el hash de los bytes actuales y
-- lo compara con este. Si no coinciden, el documento se reporta como ALTERADO
-- en vez de como firmado.
--
-- SHA-256 y no algo más sofisticado porque el objetivo aquí es la INTEGRIDAD
-- (detectar que los bytes cambiaron), no la autenticidad criptográfica frente
-- a un tercero. Para eso último haría falta una firma digital con certificado
-- emitido por una entidad de certificación, que es una decisión institucional
-- —y un costo— que todavía no está confirmada. Esta migración es el requisito
-- previo de esa conversación, no su reemplazo: sin huella no hay nada que
-- certificar.
--
-- POR QUÉ TAMBIÉN firmado_por_id
-- ------------------------------
-- La identidad de quien firmó solo existía dentro del texto libre de un
-- registro de auditoría ("... firmado por Fulano (FIRMA-XXXX)"). Un texto no
-- se puede consultar ni unir con la tabla de usuarios, y se rompe si la
-- persona cambia de nombre. La firma queda atada a la cuenta como una clave
-- foránea de verdad. ON DELETE SET NULL —y no CASCADE— porque borrar una
-- cuenta jamás debe borrar la evidencia de lo que esa cuenta firmó.
--
-- LOS DOCUMENTOS YA FIRMADOS SE QUEDAN EN NULL, A PROPÓSITO
-- --------------------------------------------------------
-- Sería trivial rellenar la columna con encode(sha256(contenido), 'hex') para
-- las filas que ya tienen firma_id. Sería también una mentira: ese hash
-- describiría los bytes de HOY, no los que había cuando se firmó, y afirmaría
-- integridad sobre un documento que pudo cambiar antes de esta migración. Se
-- dejan en NULL y la verificación los reporta explícitamente como "firmados
-- antes de que se registrara la huella: no verificables".
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE documentos
    ADD COLUMN IF NOT EXISTS firma_hash_sha256 VARCHAR(64);

ALTER TABLE documentos
    ADD COLUMN IF NOT EXISTS firmado_por_id BIGINT REFERENCES usuarios (id) ON DELETE SET NULL;

-- El hash siempre es SHA-256 en hexadecimal en minúsculas: 64 caracteres de
-- [0-9a-f]. La restricción impide que una escritura futura guarde aquí Base64,
-- mayúsculas o un hash de otro algoritmo, que pasarían desapercibidos hasta
-- que una verificación fallara sin motivo aparente.
ALTER TABLE documentos
    DROP CONSTRAINT IF EXISTS ck_documentos_firma_hash;
ALTER TABLE documentos
    ADD CONSTRAINT ck_documentos_firma_hash
    CHECK (firma_hash_sha256 IS NULL OR firma_hash_sha256 ~ '^[0-9a-f]{64}$');

CREATE INDEX IF NOT EXISTS idx_documentos_firmado_por ON documentos (firmado_por_id);
