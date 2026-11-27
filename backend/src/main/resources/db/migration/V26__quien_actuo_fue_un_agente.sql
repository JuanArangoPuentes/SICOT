-- ═══════════════════════════════════════════════════════════════════════════
-- Distinguir «lo hizo la persona» de «lo hizo un agente con su cuenta».
--
-- POR QUÉ
-- -------
-- `registros.origen` (V15) tenía dos valores, USUARIO y SISTEMA, y su propio
-- comentario explica muy bien por qué hacían falta: un `usuario_id` nulo era
-- ambiguo —«lo hizo el sistema» o «se perdió el dato»— y en un expediente de
-- contratación pública esa ambigüedad es la diferencia entre información
-- completa e información sospechosa.
--
-- El mismo razonamiento deja fuera un tercer caso que ya existe. El servidor
-- MCP (`mcp/`) se autentica contra /api/auth/login con las credenciales de UNA
-- CUENTA REAL (SICOT_EMAIL / SICOT_PASSWORD) y actúa como esa persona: sus
-- herramientas crean contratos, asignan supervisores, cambian estados y cierran
-- sub-pasos. Todo eso quedaba en la auditoría con origen USUARIO y el
-- `usuario_id` del funcionario, es decir, INDISTINGUIBLE de lo que esa persona
-- hizo con sus manos en la pantalla.
--
-- Para un sistema cuyo propósito entero es producir un expediente auditable,
-- «esto lo decidió una persona» frente a «esto lo propuso un modelo de lenguaje
-- actuando con sus credenciales» es justo la distinción que el expediente tiene
-- que conservar. El proyecto ya tiene la regla escrita —«la IA es asesora, nunca
-- autoridad», .github/copilot-instructions.md §29— y el MCP era el único punto
-- donde la trazabilidad no la sostenía.
--
-- QUÉ SE AÑADE
-- ------------
-- 1. El valor AGENTE en el CHECK de `origen`. Sigue habiendo `usuario_id`: la
--    cuenta responsable es la misma y eso no cambia. Lo que cambia es que
--    ahora se puede leer quién movió la mano.
-- 2. `agente`, el nombre que declaró la herramienta. «AGENTE» a secas
--    responde la mitad de la pregunta; en una revisión la pregunta completa es
--    «¿qué agente?». Nula en todo lo anterior y en todo lo que haga una
--    persona.
--
-- LO QUE ESTO NO ES
-- -----------------
-- No es un control de acceso. Un cliente que no manda la cabecera queda como
-- USUARIO, igual que antes, así que esto NO impide que alguien use la API
-- automáticamente sin declararse. No pretende impedirlo: sirve para que el
-- agente propio del proyecto —el único que hay— sea honesto sobre lo que es, y
-- para que mañana se pueda exigir la cabecera si hace falta. Un control que
-- depende de que el cliente colabore es documentación, no una barrera, y se
-- dice aquí para que nadie lo confunda con una.
-- ═══════════════════════════════════════════════════════════════════════════

-- La columna es VARCHAR(10) desde V15 y 'AGENTE' son 6 caracteres: cabe.
ALTER TABLE registros DROP CONSTRAINT IF EXISTS ck_registros_origen;
ALTER TABLE registros
    ADD CONSTRAINT ck_registros_origen CHECK (origen IN ('USUARIO', 'SISTEMA', 'AGENTE'));

-- Quién se declaró. Se acota a 60 caracteres porque lo elige el cliente y va a
-- una pantalla: sin tope, una cabecera de miles de caracteres entraría en la
-- auditoría y rompería la tabla al mostrarla.
ALTER TABLE registros ADD COLUMN agente VARCHAR(60);

-- Para la pregunta de una revisión: «¿qué hizo aquí algo automático?». Parcial,
-- porque la inmensa mayoría de las filas tiene la columna nula y un índice que
-- las incluyera sería casi todo el índice para nada.
CREATE INDEX idx_registros_agente ON registros (agente) WHERE agente IS NOT NULL;
