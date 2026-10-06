# Histórico

Lo que hay aquí **describe el SICOT de una fecha, no el de hoy**. Son fotos:
sirven para entender por qué el código es como es, no para saber cómo funciona
ni cómo se opera. Nada de esta carpeta es una instrucción a seguir.

Está separado del resto de `docs/` justamente por eso: al estar mezclado con la
documentación vigente, un documento fechado se lee como si siguiera valiendo.

| Documento | Qué es |
| --- | --- |
| [`AUDITORIA_2026-09-08.md`](./AUDITORIA_2026-09-08.md) | Auditoría técnica del repositorio justo después de integrar el motor de automatizaciones |
| [`AUDITORIA_MOVIL_2026-09-16.md`](./AUDITORIA_MOVIL_2026-09-16.md) | Medición de la interfaz en pantalla de teléfono, entrada del trabajo de adaptación a móvil |
| [`REVISION_ARQUITECTURA_2026-09-08.md`](./REVISION_ARQUITECTURA_2026-09-08.md) | Revisión crítica de arquitectura y qué se corrigió a partir de ella |
| [`orca/`](./orca) | La flota de agentes que ejecutó seis tareas en agosto y septiembre de 2026, cerrada desde entonces. Los briefs de `orca/tareas/` cuentan qué cambió cada una |
| [`FASE_0_INSPECCION.md`](./FASE_0_INSPECCION.md), [`FASE_2_AUDITORIA_BD.md`](./FASE_2_AUDITORIA_BD.md), [`SICOT_CHECKPOINT_FASE_3.md`](./SICOT_CHECKPOINT_FASE_3.md), [`AUDIT_QUERIES.sql`](./AUDIT_QUERIES.sql) | Reportes y consultas de las fases iniciales del proyecto |

Las decisiones que **siguen vigentes** no están aquí: están en
[`docs/decisiones/`](../decisiones) como ADR. Una decisión que se revierte no se
mueve a esta carpeta — se escribe un ADR nuevo que la sustituya, para que la
decisión y su motivo queden en el mismo sitio.

**Al añadir algo aquí:** un documento fechado y cerrado entra en esta carpeta
desde el principio, y si alguien puede confundirlo con documentación vigente,
con un aviso al comienzo que diga de qué fecha habla.
