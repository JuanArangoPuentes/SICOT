# ADR-003 — Cuándo dejar de guardar los archivos dentro de PostgreSQL

**Estado:** Aceptada · **Fecha:** 1 de septiembre de 2026

## Contexto

Los archivos —evidencias de contrato y plantillas del catálogo— se guardan como
columnas `BYTEA` dentro de las tablas `documentos` y `formatos_documentales`,
con un tope de 20 MB por archivo.

Esto **es correcto hoy** y por buenos motivos: un solo `pg_dump` captura
absolutamente todo, un archivo y su fila viven en la misma transacción (no
existe el estado "la fila dice que hay archivo pero el archivo no está"), y no
hay un segundo sistema que instalar, respaldar y vigilar.

También tiene un techo. PostgreSQL no es un almacén de objetos: cada archivo
inflado por TOAST viaja por la misma conexión que las consultas, engorda cada
respaldo y alarga cada restauración. El problema es que ese techo **no avisa**:
el sistema se degrada de forma gradual hasta que un día la restauración no cabe
en el RTO de [ADR-002](./ADR-002-continuidad-y-perdida-aceptable.md).

Punto de partida medido el 1 de septiembre de 2026: base completa **8,6 MB**,
tabla `documentos` **48 kB**, **cero** archivos almacenados. Todo lo que sigue es
prospectivo.

## Decisión

Los archivos siguen en `BYTEA`. Se fija un **umbral medido y vigilado**, no una
intuición: se migra a almacenamiento de objetos cuando se cumpla **cualquiera**
de estas tres condiciones.

| # | Disparador | Se mide con |
| --- | --- | --- |
| 1 | La tabla `documentos` supera **5 GB** | Métrica `sicot.almacenamiento.documentos.bytes` en `/actuator/metrics` |
| 2 | El respaldo diario tarda más de **10 minutos** | Duración registrada por `scripts/respaldo-sicot.sh` |
| 3 | Una restauración de prueba supera el **RTO de 4 horas** | Ensayo de restauración |

El disparador 1 no exige que nadie se acuerde de mirar: el backend **registra un
aviso en el log** al arrancar y a diario cuando supera el 80 % del umbral, de
modo que la conversación empieza con margen y no cuando ya duele.

**Destino cuando toque migrar:** MinIO, compatible con S3, de código abierto y
autoalojable — cumple la regla de que todo en SICOT sea gratuito y sin límite de
uso. La tabla conserva la referencia y el hash SHA-256; el archivo se va fuera.

## Consecuencias

**Lo que se gana.** El umbral deja de ser una opinión. Cuando alguien pregunte
"¿esto ya está grande?", hay un número y una métrica que responde.

**Lo que se pierde.** Nada hoy. La deuda queda registrada y vigilada en vez de
olvidada.

**Lo que hay que respetar al migrar.** La huella SHA-256 de
`V13__huella_de_integridad_en_la_firma.sql` tiene que seguir verificándose
contra el archivo en su nueva ubicación. Si la migración rompe la verificación
de integridad, se pierde la única garantía de que un documento firmado no fue
alterado — que es exactamente lo que no se puede perder.

**Por qué 5 GB y no otra cifra.** Es el punto en que un `pg_dump` completo
empieza a tardar minutos en vez de segundos sobre hardware modesto, y donde la
restauración deja de ser trivial. No es un límite de PostgreSQL —aguanta mucho
más— sino el punto donde deja de cumplirse cómodamente el RTO comprometido.

## Cuándo revisar

- Cuando cualquiera de los tres disparadores se active.
- Si el tope de 20 MB por archivo sube: eso mueve la fecha de vencimiento hacia
  adelante y obliga a recalcular el umbral.
