# Tarea 3 — Seguridad del módulo IA · rama `fix/seguridad-modulo-ia`

> Lee primero `.github/copilot-instructions.md` (§29 y §30 en particular) y
> `.claude/orca/CONTRATO_DEL_AGENTE.md`.

## Objetivo

Cerrar la superficie de ataque del Copiloto. El módulo de IA es el único punto de
SICOT donde entra contenido que **nadie del equipo escribió** (un PDF que sube
Gestión, una pregunta que escribe el supervisor) y sale hacia un modelo que
después responde a un funcionario sobre un contrato del Estado. Todo lo que entre
ahí es dato no confiable.

## Alcance — archivos que **posee** esta rama

- `backend/src/main/java/co/sena/sicot/ia/**`
- `backend/src/main/java/co/sena/sicot/dto/ia/**`
- `backend/src/main/java/co/sena/sicot/controller/IAController.java`
- `backend/src/main/java/co/sena/sicot/controller/CopilotoController.java`
- Pruebas nuevas bajo `backend/src/test/**`.

## Fuera de alcance

- Todo lo que no sea IA. En particular: no reescribas `ArchivoValidator`
  (es de la tarea 1) — **úsalo**.
- Cambiar de modelo o de proveedor de IA. SICOT corre sobre **Ollama local**
  (§29) y debe seguir siendo gratuito e ilimitado: nada de APIs de pago, ni
  "solo para pruebas".
- Debilitar `CopilotoChatService.CONOCIMIENTO_PROCESO`. Esa constante existe
  porque el modelo ya alucinó una vez una función de carga de archivos que no
  existe (§29). Se puede endurecer, nunca aflojar.

## Hallazgos ya verificados (punto de partida, no lista cerrada)

1. **`IAController.extraerContrato` no valida los archivos.**
   `POST /api/ia/extraer-contrato` recibe `List<MultipartFile>` y va directo a
   `ExtraccionContratoService`. `ExtraccionContratoService.extraer` limita la
   *cantidad* (`MAX_ARCHIVOS = 6`) y el tiempo total (`presupuesto-extraccion`),
   pero **nadie comprueba el tamaño ni el tipo real** de cada archivo: no se
   llama a `ArchivoValidator`, que es justo la pieza que ya existe para esto y
   que sí usan `DocumentoService` y `FormatoDocumentalService`. Seis archivos de
   500 MB entran a memoria antes de que nadie diga nada. Es el hallazgo más
   grave de esta rama.
2. **`dto/ia/ChatRequest.historial` no tiene tope.** Ni número de turnos, ni
   longitud por turno, ni validación de `rol` (se espera `"user"`/`"ai"`, se
   acepta cualquier cosa). El historial lo manda el cliente entero en cada
   petición y se interpola en el prompt: un historial fabricado puede poner
   palabras en boca de la IA en turnos anteriores. `pregunta` tiene `@NotBlank`
   pero ningún `@Size` máximo.
3. **Inyección de prompt por contenido de documento.** El texto que
   `PdfTextExtractor` saca del PDF se interpola en el prompt sin delimitar ni
   neutralizar. Un PDF que contenga instrucciones dirigidas al modelo es, hoy,
   indistinguible del prompt del sistema. Delimita el contenido no confiable de
   forma explícita e instruye al modelo a tratarlo como datos, nunca como
   órdenes. Lo mismo aplica a los campos del contrato que se interpolan en
   `CopilotoChatService.responder` (objeto, contratista…): los escribe un usuario.
4. **Revisa el control de acceso del chat.** `CopilotoChatService.responder`
   confía en que `contratoService.buscar(contratoId)` ya aplicó
   `SecurityUtils.verificarAccesoAlContrato`. Confírmalo con una prueba real, no
   leyendo el comentario: es la única barrera entre un supervisor y el contrato
   de otro. (La tarea 5 cubre este mismo punto desde el lado de las pruebas;
   coordinen para no duplicar.)
5. **Fugas por log y por error.** Verifica que ni el prompt completo, ni el texto
   del PDF, ni la URL interna de Ollama salgan en logs de nivel `INFO` o en un
   mensaje de error hacia el cliente. Ya hubo un arreglo previo por esto
   (commit `02b7c12`, "Deja de filtrar la URL interna de Ollama al cliente"):
   comprueba que no se reintrodujo por otra vía.
6. **`GenerarDocumentoRequest.tipo` es un `String` libre** que se resuelve contra
   `PlantillaDocumentoIA.CATALOGO`. Comprueba qué pasa con un tipo desconocido:
   debe ser un 400 claro, no una plantilla vacía ni un 500.

## Reglas específicas de esta rama

- La IA es **asesora, nunca autoridad** (§29): ningún arreglo tuyo puede
  convertir una salida del modelo en una decisión automática.
- Si Ollama no está disponible, el sistema **falla honestamente**
  (`IaNoDisponibleException` → 503). No añadas fallbacks que fabriquen una
  respuesta plausible: eso es exactamente lo que prohíbe §30.
- Los límites que pongas (tamaño, turnos, caracteres) deben ser generosos con el
  uso real: la extracción real ronda 35-40 s por archivo en máquinas sin GPU.
  Cortas el caso patológico, no el uso normal.

## Criterios de aceptación

- [ ] Ningún archivo llega a `PdfTextExtractor` sin haber pasado por
      `ArchivoValidator` (tamaño + tipo real por bytes mágicos).
- [ ] `ChatRequest` tiene topes explícitos y probados.
- [ ] El contenido no confiable está delimitado en el prompt y el sistema
      instruido para no obedecerlo.
- [ ] Existe una prueba que demuestra que un supervisor **no** puede chatear
      sobre un contrato ajeno.
- [ ] Ningún log ni respuesta filtra prompt completo, texto de documento o URL
      interna.
- [ ] `cd backend; .\mvnw.cmd -B -ntp verify` en verde.

---

## Resultado — completada, PR #7 mergeado

Commits `ddfc817`, `f34293a`. `verify` en verde: 64 pruebas (10 nuevas en
`IaSeguridadIntegrationTest`).

- **El hallazgo principal, cerrado:** `POST /api/ia/extraer-contrato` ahora pasa
  cada archivo por `ArchivoValidator` —tamaño y tipo real por bytes mágicos—
  igual que hacían documentos y formatos.
- Nueva clase `EntradaNoConfiable`: delimita en el prompt el contenido de origen
  no confiable (texto de PDF, pregunta, historial, campos libres del contrato) e
  instruye al modelo a tratarlo como datos y a no obedecer órdenes que aparezcan
  dentro.
- `ChatRequest` tiene topes de turnos y de longitud, que antes no existían.

**Cambio de comportamiento visible:** un lote que incluya un archivo de tipo no
permitido o sobredimensionado devuelve **400 para toda la petición**, en vez de
saltárselo en silencio. Es lo mismo que ya hacían `DocumentoService` y
`FormatoDocumentalService`.

Sigue sin haber ningún servicio de IA de pago: todo corre sobre Ollama local, y
si Ollama no está, el sistema falla honestamente con 503.
