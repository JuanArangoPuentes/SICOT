# Listas de chequeo documentales — catálogo integrado en SICOT

**Creado**: 2026-08-26 · **Estado**: catálogo cargado y servido por la API

## Qué se integró

Las **8 listas de chequeo oficiales** que entregó el equipo, transcritas de los `.xlsx`
publicados en CompromISO a un catálogo estructurado que el backend sirve por API:

| Código | Nombre del formato | Versión | Tipo | Etapas | Ítems |
|---|---|---|---|---|---|
| `GCCON-F-026` | Lista de Chequeo Contratación Directa | 04 | Modalidad de selección | 4 | 58 |
| `GCCON-F-049` | Lista de Chequeo Concurso de Méritos | 03 | Modalidad de selección | 4 | 76 |
| `GCCON-F-051` | Lista de Chequeo Licitación Pública | 03 | Modalidad de selección | 4 | 85 |
| `GCCON-F-052` | Terminación Anticipada y Liquidación | 03 | Trámite contractual | 2 | 7 |
| `GCCON-F-053` | Lista de Chequeo Mínima Cuantía | 05 | Modalidad de selección | 4 | 63 |
| `GCCON-F-055` | Lista de Chequeo Selección Abreviada por Subasta Inversa | 03 | Modalidad de selección | 4 | 75 |
| `GCCON-F-056` | Lista de Chequeo Selección Abreviada por Menor Cuantía | 03 | Modalidad de selección | 4 | 77 |
| `GRF-F-088` | Documentos Requeridos para Registro de Obligaciones | 08 | Trámite de pago | 1 | 48 |

**489 ítems en total**, que citan entre todos **26 formatos institucionales distintos**.

## Regla que se siguió: transcribir, no interpretar

El catálogo es copia literal del formato oficial. SICOT **no** reescribe nombres de documento,
no completa lo que el formato deja incompleto y no corrige sus errores. Cuando un archivo trae
una inconsistencia, queda registrada en el campo `advertencias` de esa lista — visible en la
API — en vez de arreglarse en silencio. Esto sostiene la regla del proyecto de no inventar el
proceso contractual real (ver [`.github/copilot-instructions.md`](../../.github/copilot-instructions.md)).

## Cómo está estructurado

Cada lista es un archivo en `backend/src/main/resources/listas-chequeo/<CODIGO>.json`:

```json
{
  "codigo": "GCCON-F-053",
  "nombre": "LISTA DE CHEQUEO MÍNIMA CUANTÍA",
  "version": "05",
  "proceso": "GESTIÓN CONTRACTUAL",
  "tipo": "MODALIDAD_SELECCION",
  "alcance": "MÍNIMA CUANTÍA",
  "archivoOrigen": "GCCON-F-053-ListadeChequeoMinimaCuantia_V05.xlsx",
  "hojaOrigen": "Lista de Chequeo",
  "tiposPago": [],
  "etapas": [
    {
      "nombre": "ETAPA PRECONTRACTUAL",
      "rotuladaEnOrigen": true,
      "items": [
        {
          "numero": 3,
          "documento": "Estudios previos con sus Anexos",
          "cuandoAplique": false,
          "observacion": "FORMATO GCCON-F-046\nIncluye ficha técnica",
          "formatos": ["GCCON-F-046"],
          "tiposPago": []
        }
      ]
    }
  ],
  "notas": ["* Cuando aplique."],
  "advertencias": []
}
```

Los tres campos derivados —los únicos que no son texto literal del formato— son verificables
contra el original:

- **`cuandoAplique`** — el formato marca con `*` los documentos que pueden no aplicar y lo
  explica en su propio pie de página (`* Cuando aplique.`). El asterisco se separa del nombre
  del documento y se convierte en este booleano.
- **`formatos`** — códigos de formato citados en el nombre del ítem o en su observación
  (`FORMATO GCCON-F-046` → `GCCON-F-046`). Sirve para enlazar la lista de chequeo con el
  catálogo de formatos de `/api/formatos`.
- **`rotuladaEnOrigen`** — `false` cuando el nombre de la etapa no está escrito en el formato y
  se dedujo por la posición de los ítems. Queda explícito para no presentar como oficial algo
  que el formato no dice.

### Las dos formas de lista

Las **siete listas GCCON** agrupan los documentos por etapa del proceso (precontractual,
contractual, de ejecución, poscontractual). Cada ítem aplica a toda la lista.

**GRF-F-088 es distinta**: pertenece al proceso de Gestión de Recursos Financieros y no agrupa
por etapa sino por **tipo de pago** — dedica una columna a cada tipo y marca con `X` qué
documentos exige. Por eso su detalle declara `tiposPago` y cada ítem lleva los tipos a los que
aplica:

```json
{
  "numero": 1,
  "documento": "Registro presupuestal del compromiso",
  "tiposPago": ["ADQUISICION_BIENES", "IMPORTACION_BIENES", "CONVENIOS", "…"]
}
```

Los 11 tipos de pago son `ADQUISICION_BIENES`, `IMPORTACION_BIENES`, `ADQUISICION_SERVICIOS`,
`OTROS_SERVICIOS_SIN_CONTRATO`, `SERVICIOS_OBRA_CIVIL`, `BOLSA_MERCANTIL`,
`HONORARIOS_PRESTACION_SERVICIOS`, `ANTICIPOS_Y_RECURSOS_EN_ADMINISTRACION`, `CONVENIOS`,
`CONVENIOS_COOPERACION_INTERNACIONAL` y `FONDO_VIVIENDA`. El código es un identificador de
SICOT; el `nombre` es el texto literal del encabezado de la columna.

## Cómo se consulta

| Método | Ruta | Devuelve |
|---|---|---|
| `GET` | `/api/listas-chequeo` | índice de las 8 listas (sin ítems) |
| `GET` | `/api/listas-chequeo?tipo=TRAMITE_PAGO` | índice filtrado por tipo de trámite |
| `GET` | `/api/listas-chequeo/{codigo}` | la lista completa, ej. `GCCON-F-053` |

Ambos requieren JWT; cualquier rol puede leerlos. El código no distingue mayúsculas.

## Por qué no está en la base de datos

El catálogo no es un dato transaccional: es el texto de un formato institucional. Cambia solo
cuando SENA publica una versión nueva del formato, y esa versión se integra regenerando el JSON
y volviendo a desplegar — no editando registros. Vive en el classpath por la misma razón que
`GcconP010Plantilla` vive en código: es la definición del proceso, no una instancia de él.
El backend valida el catálogo al arrancar y **falla el arranque** si un archivo está corrupto,
le falta un campo o repite un código: es preferible no arrancar a servir un catálogo incompleto
de documentos exigidos por norma.

## Cómo se regenera cuando salga una versión nueva

```bash
pip install openpyxl
python backend/tools/extraer_listas_chequeo.py <carpeta-con-los-xlsx>
```

El script reescribe `backend/src/main/resources/listas-chequeo/`. Después:

```bash
cd backend && mvn test
```

`ListaChequeoServiceTest` verifica el catálogo real (no datos de prueba): si la transcripción se
rompe, las pruebas fallan antes de que el catálogo llegue a un usuario.

---

## Hallazgos de la revisión de los archivos originales

Esto es lo que apareció al leer los 8 `.xlsx`. **Ninguno se corrigió en el catálogo** — se
transcribió lo que dice el archivo y se dejó registrado. Son puntos para llevar a la reunión de
levantamiento institucional.

### Detectados automáticamente (quedan en `advertencias` de la API)

1. **La versión del nombre del archivo no coincide con la del encabezado** en cuatro formatos:
   `GCCON-F-051` (archivo V02 / hoja 03), `GCCON-F-052` (archivo V04 / hoja 03), `GCCON-F-055`
   (archivo V02 / hoja 03) y `GCCON-F-056` (archivo V02 / hoja 03). El catálogo conserva la del
   encabezado, que es la que está dentro del documento. **Hay que confirmar cuál es la vigente.**
2. **`GCCON-F-053` numera dos ítems distintos con el número 58**: "Procedimiento de
   incumplimiento" aparece tanto en ETAPA DE EJECUCIÓN como en ETAPA POSCONTRACTUAL con el mismo
   número. Las otras cinco listas de modalidad repiten ese mismo documento en las dos etapas
   pero con números consecutivos distintos, así que la repetición es intencional y la numeración
   de F-053 es el error.
3. **`GCCON-F-055` no rotula su primera etapa**: la fila del título viene vacía. Los 43 ítems
   anteriores a ETAPA CONTRACTUAL se agruparon como ETAPA PRECONTRACTUAL, marcados con
   `rotuladaEnOrigen: false`.
4. **`GCCON-F-056` trae una hoja `Hoja2`** con una lista más antigua y distinta (cita
   Resol. 751 de 2014 y Circular 115 de 2014, e incluye ítems como "Designación Líder de
   Proceso" y "Numeración del proceso" que no están en la hoja oficial). Parece un borrador
   olvidado; no se incluyó.
5. **`GRF-F-088` tiene tres columnas duplicadas y vacías** (N, O y P repiten "Convenios",
   "Convenios de Cooperación Internacional" y "Fondo de Vivienda" de las columnas K, L y M, sin
   marcar ningún documento). No se incluyeron como tipos de pago.

### Detectados leyendo los archivos (no automatizables)

6. **El "Anexo de Verificación Criterios de Contratación" se cita con dos códigos distintos
   dentro del mismo archivo.** La hoja de la lista dice `GCCON-F-053` en la columna
   OBSERVACIONES, pero la hoja INSTRUCTIVO del mismo archivo dice `FORMATO GCCON-AN-001`.
   Ocurre igual en F-026, F-049, F-051, F-053, F-055 y F-056. `GCCON-F-053` es el código de la
   propia lista de mínima cuantía, así que lo más probable es que el correcto sea
   `GCCON-AN-001` — pero **no se asumió**: el catálogo transcribe lo que dice cada hoja.
7. **La hoja INSTRUCTIVO de `GCCON-F-052` está titulada "Lista de Chequeo Concurso de Meritos"**
   — texto copiado de `GCCON-F-049`. El contenido sí corresponde a terminación anticipada y
   liquidación.
8. **`GCCON-F-053` tiene un pie de página `** Aplica Resolución 069 de 2014` pero ningún ítem
   de la hoja lleva la marca `**`.** Además, su INSTRUCTIVO describe un "Acta de comité de
   contratación\*\*" que no existe en la hoja de la lista, aunque las otras cinco modalidades sí
   la incluyen (ítem 12 en todas). Falta un ítem o sobra una instrucción.
9. **El INSTRUCTIVO de `GCCON-F-026` describe dos documentos que no están en su hoja de lista**:
   "Designación Líder de Proceso\*" y "Numeración para contratación directa".
10. **Los códigos vienen escritos de forma inconsistente en los encabezados**: `GCCON- F-026`,
    `GCCON - F- 056`, `GCCON-F-053`. El catálogo los normaliza sin espacios.
11. **`GRF-F-088` marca la fila 23 con `x` minúscula** donde el resto del formato usa `X`. Se
    interpretó como marca válida.
12. **La hoja "Instrucciones" de `GRF-F-088` enumera 10 tipos de pago que no coinciden con las
    columnas de la hoja del formato**: falta "Honorarios Prestación de Servicios Personales"
    (columna I, que sí exige 11 documentos propios) y los nombres no son los mismos.
13. **Erratas de digitación transcritas tal cual**, por trazabilidad: "Designación del evaluador
    o Comité evlauador" (F-053 ítem 20), "Otros documentos de ejecuciòn", "Puede ser en fìsico o
    digital", "ejecuión", "Adquiciones", "materialziar".

### Lo que estas listas sí confirmaron del proceso

Los formatos que las 8 listas citan cruzan con lo que SICOT ya modela y **resuelven una duda
que estaba abierta** sobre los documentos de cierre:

- `GCCON-F-018` = **Acta de inicio** (ya modelado en la subetapa 2.7).
- `GCCON-F-031` = **Informe / Certificación de pago de supervisión** (subetapa 3.4).
- `GCCON-F-030` = **Informe final de supervisión** — confirmado como documento propio,
  distinto del acta de liquidación (subetapa 6.3).
- `GCCON-F-027` = **Acta de liquidación o terminación anticipada** — este código no estaba
  confirmado antes.
- `GCCON-F-028` = **Acta de cierre de expediente**.
- `GCCON-F-032` = **Designación de supervisor** (citado en F-026 ítem 36).
- `GCCON-F-071` = **Informe de supervisión para modificación**.
- `GRF-F-089` = **Comunicación interna para trámite de pagos** (ítem 37 de GRF-F-088),
  consistente con que lo firma el Ordenador del gasto y no el supervisor.

Los otros formatos citados (`GCCON-F-021`, `-F-022`, `-F-023`, `-F-037`, `-F-038`, `-F-039`,
`-F-046`, `-F-075`, `-F-076`, `GD-F-010`, `GJ-F-026`, `GIL-F-029`, `GRF-F-063`, `-F-064`,
`-F-078`, `-F-087`, `GTH-074`) son precontractuales o financieros y **no** están modelados hoy
en SICOT. Ninguno se agregó al flujo: eso se define en la reunión de levantamiento.

## Qué queda pendiente

- **Nada de esto se ve todavía en la interfaz.** El catálogo se sirve por API pero ninguna
  pantalla lo consume aún.
- **No está enlazado al flujo de un contrato.** Un contrato en SICOT no declara todavía por qué
  modalidad se seleccionó, así que el sistema no puede decir "a este contrato le aplica
  GCCON-F-053". Ese enlace requiere decidir dónde vive la modalidad en el modelo de contrato.
- **Los 26 formatos citados no están cargados** en el catálogo de `/api/formatos`. El campo
  `formatos` de cada ítem ya deja lista la conexión para cuando se carguen.
