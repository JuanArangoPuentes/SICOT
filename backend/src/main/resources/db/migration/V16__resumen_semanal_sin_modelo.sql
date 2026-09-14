-- ═══════════════════════════════════════════════════════════════════════════
-- El resumen semanal deja de pasar por el modelo de lenguaje.
--
-- POR QUÉ SE QUITA LA IA DE ESTA REGLA
-- ------------------------------------
-- La regla `resumen-semanal-ia` era la única del motor que consumía el modelo
-- local, y la única cuyo texto no se podía verificar. El 10 de septiembre de
-- 2026 se midió en esta máquina, con el prompt real y los tres tamaños de
-- modelo que caben en un portátil corriente:
--
--   qwen2.5:7b   → 2 de 6 salidas sin errores de hecho
--   qwen2.5:3b   → 0 de 6
--   qwen2.5:1.5b → 0 de 6
--
-- Endurecer el prompt arregló el formato y no la fidelidad. Los fallos no eran
-- de estilo: «12 subetapas (44%)» donde eran 11 y 41%; «la subetapa 3.3
-- continúa en curso» en la misma frase que la daba por completada; «el 41% del
-- plazo total» confundiendo avance con plazo; y, en la prueba de punta a punta,
-- los códigos internos de auditoría (`ETAPA_ACTUALIZADA`) volcados al texto que
-- lee el supervisor, junto a la palabra inexistente «veintís» en lugar de
-- «veintisiete».
--
-- El diagnóstico no es que el modelo fuera pequeño. Es que se le estaba pidiendo
-- lo único que un modelo de lenguaje no garantiza —sostener hechos— cuando esos
-- hechos ya venían calculados en Java desde antes de construir el prompt. La
-- segunda regla invariante de ADR-008 decía «la regla decide, la IA solo
-- redacta»; lo que estas mediciones muestran es que ni siquiera redactar sin
-- alterar los datos es algo que se le pueda encargar aquí.
--
-- Ahora el texto se compone con plantillas a partir de los mismos datos. Es
-- correcto por construcción, tarda microsegundos en lugar de ~100 s, y funciona
-- en un equipo que no tenga Ollama instalado — que era el objetivo de fondo del
-- motor: que SICOT no dependa de un modelo grande para funcionar bien.
--
-- El modelo local sigue en SICOT donde sí aporta y su trabajo no es repetir
-- cifras: el chat del copiloto y la extracción de datos de un PDF.
--
-- QUÉ HACE ESTA MIGRACIÓN
-- -----------------------
-- Renombra el tipo de tarea, que se persiste como texto y está sujeto a un
-- CHECK. Se migran también las filas que existan: una tarea encolada con el
-- nombre viejo dejaría de mapear contra el enum y fallaría al leerse, que es un
-- fallo silencioso hasta que alguien mira la cola.
-- ═══════════════════════════════════════════════════════════════════════════

-- El CHECK se suelta ANTES de tocar las filas: con la restricción vigente, el
-- UPDATE a un valor que todavía no está en la lista sería rechazado.
ALTER TABLE tareas_automatizadas DROP CONSTRAINT IF EXISTS ck_tareas_automatizadas_tipo;

UPDATE tareas_automatizadas
   SET tipo = 'REDACTAR_RESUMEN'
 WHERE tipo = 'REDACTAR_RESUMEN_IA';

-- Las claves de idempotencia llevan el código de la regla, que también cambia.
-- Sin esto, la primera evaluación tras el despliegue volvería a encolar el
-- resumen de una semana que ya se había resumido: la clave nueva no chocaría
-- con la vieja y la restricción UNIQUE no la pararía.
UPDATE tareas_automatizadas
   SET clave_idempotencia = replace(clave_idempotencia, 'resumen-semanal-ia:', 'resumen-semanal:')
 WHERE clave_idempotencia LIKE 'resumen-semanal-ia:%';

UPDATE tareas_automatizadas
   SET regla = 'resumen-semanal'
 WHERE regla = 'resumen-semanal-ia';

ALTER TABLE tareas_automatizadas
    ADD CONSTRAINT ck_tareas_automatizadas_tipo
    CHECK (tipo IN ('CREAR_ALERTA', 'ENVIAR_CORREO', 'REDACTAR_RESUMEN'));
