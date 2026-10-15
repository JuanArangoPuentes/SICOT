-- ═══════════════════════════════════════════════════════════════════════════
-- Datos que el proceso real del supervisor necesita y SICOT no guardaba.
--
-- POR QUÉ
-- -------
-- El 15-10-2026 el supervisor Alex Zapata documentó cómo se supervisa de verdad
-- un contrato en el Centro (docs/producto/LISTA_CHEQUEO_ALEX.md), sobre el
-- contrato CO1.PCCNTR.10057820. Cuatro datos de ese proceso no tenían dónde
-- quedar:
--
--   enlace_secop        El acto de designación (GCCON-F-032) trae el enlace del
--                       proceso en SECOP II, y el supervisor lo usa para
--                       verificar cantidades, especificaciones y valores. No es
--                       derivable del número de contrato: son identificadores
--                       distintos (CO1.NTC.… frente a CO1.PCCNTR.…).
--   apoyo_supervision   La designación nombra, además del supervisor, al «apoyo
--                       a la supervisión», que es quien registra el contrato en
--                       SICOT. Se guarda el nombre tal como lo trae el documento.
--   fecha_designacion   La fecha del acto de designación: desde ahí corre el
--                       plazo de un día para manifestar conflicto de interés.
--   correo_contratista  El acta de inicio trae el correo del contratista, y el
--                       supervisor le escribe para acordar las entregas.
--
-- Y una columna en subetapas, documentos_requeridos, para el paso del acta de
-- inicio: según el proceso real, SICOT no la genera sino que la recibe, y el
-- paso no se cierra hasta que el supervisor carga el acta firmada y la
-- evidencia de que la devolvió. La marca la pone la plantilla al sembrar las
-- etapas de un contrato nuevo; los contratos que ya existen conservan las
-- subetapas con que se crearon y quedan sin marca, así que nada cambia para
-- ellos.
--
-- Todo es opcional (NULL): un contrato registrado sin designación sigue siendo
-- válido.
-- ═══════════════════════════════════════════════════════════════════════════
ALTER TABLE contratos ADD COLUMN enlace_secop VARCHAR(500);
ALTER TABLE contratos ADD COLUMN apoyo_supervision VARCHAR(255);
ALTER TABLE contratos ADD COLUMN fecha_designacion DATE;
ALTER TABLE contratos ADD COLUMN correo_contratista VARCHAR(255);

-- Lo que viene de un PDF no se guarda como enlace si no es del SECOP: el panel
-- lo muestra como algo que se puede abrir, y un enlace arbitrario ahí es justo
-- lo que no se quiere.
ALTER TABLE contratos ADD CONSTRAINT ck_contratos_enlace_secop
    CHECK (enlace_secop IS NULL OR enlace_secop LIKE 'https://community.secop.gov.co/%');

ALTER TABLE subetapas ADD COLUMN documentos_requeridos VARCHAR(40);
ALTER TABLE subetapas ADD CONSTRAINT ck_subetapas_documentos_requeridos
    CHECK (documentos_requeridos IS NULL OR documentos_requeridos IN ('ACTA_FIRMADA_Y_ENVIO'));
