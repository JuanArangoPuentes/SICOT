-- ═══════════════════════════════════════════════════════════════════════════
-- Los bienes que el supervisor le pide al contratista: la solicitud de pedido.
--
-- POR QUÉ
-- -------
-- El 20-10-2026 Alex Zapata completó el paso 5 de su lista de chequeo
-- (docs/producto/LISTA_CHEQUEO_ALEX.md), que hasta entonces solo decía
-- «contacto a contratista». Lo que agregó en «PROCESO EN EL APLICATIVO» y
-- «REQUERIMIENTOS» es concreto:
--
--   «Se debe generar una preforma de correo […] donde se relaciona un formato
--    con el listado de partes y suministros a recibir, este será un documento
--    de excel adjunto en el correo.»
--   «1 - Crear preforma de correo. 2 - Generar y adjuntar el documento excel
--    con la lista de materiales solicitado (definir cómo será este proceso y
--    dónde se encontrará esta documentación).»
--
-- Y adjuntó el formato real que usa hoy: «Solicitud_Maybe.xlsx», la solicitud
-- de pedido del Lote 4 —35 ítems con cantidad, valor unitario con IVA y valor
-- total, el presupuesto asignado del contrato, lo comprometido, lo que queda, y
-- su firma como supervisor—. Ese archivo es la fuente de esta tabla: SICOT
-- reproduce ese formato en vez de inventar uno.
--
-- La preforma de correo ya existía (BorradorDeCorreo.tsx) y dejaba un hueco
-- entre corchetes: «[Relacione aquí cada bien con su cantidad exacta, según la
-- oferta económica]». Los ítems no tenían dónde vivir, así que el supervisor
-- rellenaba el hueco a mano, cada vez, por fuera de SICOT.
--
-- DE DÓNDE SALEN LOS ÍTEMS
-- ------------------------
-- Los captura el supervisor, pegándolos de la oferta económica adjudicada. No
-- los puede traer SICOT: las cantidades y especificaciones están en el PDF de
-- la oferta, que vive en el expediente del proceso en SECOP II, y ese
-- expediente no es alcanzable desde un programa —el índice de archivos de los
-- datos abiertos está muerto desde 2021 y el enlace de descarga responde 403
-- (ver docs/producto/PROCESO_REAL_EN_SICOT.md, paso 4)—. Pegar la tabla desde
-- el Excel de la oferta es la vía que sí existe hoy, y queda guardada una vez
-- en lugar de rehacerse en cada correo.
--
-- QUÉ NO SE GUARDA
-- ----------------
-- El valor total del ítem (cantidad × valor unitario) y el subtotal de la
-- solicitud no son columnas: los calcula SICOT al leerlos. Guardar un total
-- junto a sus factores es guardar la misma verdad dos veces, y basta con que
-- alguien corrija una cantidad por SQL para que el archivo que recibe el
-- contratista diga una cosa y la suma otra.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE items_solicitud_pedido (
    id              BIGSERIAL PRIMARY KEY,
    contrato_id     BIGINT NOT NULL REFERENCES contratos (id) ON DELETE CASCADE,

    -- El número con el que el ítem aparece en la oferta económica y en el
    -- archivo que se le manda al contratista. Es el orden de la solicitud, no
    -- el id: el contratista y el supervisor hablan por teléfono de «el ítem 12».
    numero          INTEGER NOT NULL,

    -- La especificación completa del bien, tal como está en la oferta
    -- económica. En el formato real del Lote 4 hay ítems de más de 900
    -- caracteres (la ficha técnica entera del mouse, del SSD, de la memoria):
    -- recortarla cambiaría lo que se le exige al contratista, así que TEXT.
    descripcion     TEXT NOT NULL,

    -- Cantidad de unidades a recibir. Entera: en el formato real todas lo son
    -- (83 mouse, 20 SSD, 0 en los ítems que no se piden). El 0 es válido y
    -- significa «estaba en la oferta y no se pide»: así se ve en el archivo de
    -- Alex, y por eso no hay CHECK > 0.
    cantidad        INTEGER NOT NULL CHECK (cantidad >= 0),

    -- Valor unitario CON IVA, como en el formato real. La precisión es la misma
    -- que la del valor del contrato, y la escala 2 alcanza: el archivo del Lote
    -- 4 trae centavos ($ 29.462,51).
    valor_unitario  NUMERIC(18, 2) NOT NULL CHECK (valor_unitario >= 0),

    -- Un contrato no puede tener dos ítems con el mismo número: el archivo que
    -- recibe el contratista quedaría con dos filas «12» y nadie sabría cuál
    -- vale. Lo exige la base y no solo el servicio.
    CONSTRAINT uk_item_solicitud_numero UNIQUE (contrato_id, numero)
);

-- La solicitud se lee entera y siempre por contrato (para armar el archivo y
-- para mostrar la tabla en el paso 2.4), nunca por id de ítem.
CREATE INDEX idx_items_solicitud_contrato ON items_solicitud_pedido (contrato_id, numero);
