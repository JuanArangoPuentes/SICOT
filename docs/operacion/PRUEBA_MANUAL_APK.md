# Prueba manual del APK antes de publicar una versión

**Por qué existe este documento, y por qué es manual.** Las pruebas de extremo a
extremo del CI corren en un navegador con tamaño de teléfono. Comprueban que la
interfaz cabe y que las tareas funcionan en el navegador. Lo que **no** pueden
ver es lo que hace distinto el envoltorio de Android, y el 18 de septiembre de
2026 cinco de doce tareas del supervisor estaban rotas justo ahí, cuatro de ellas
sin ningún mensaje ([auditoría, apéndice del 18](../AUDITORIA_MOVIL_2026-09-16.md)).

Esta lista es la forma de no volver a descubrirlo en el teléfono de un
supervisor. Está escrita como lo que es —una comprobación a mano— y no disfrazada
de prueba automática.

Se hace con **el APK firmado de la versión**, descargado de GitHub, no con uno
compilado en la máquina: es lo que va a instalar el supervisor.

## Preparación

- Un teléfono Android con bloqueo de pantalla. Un emulador sirve, pero con la GPU
  del anfitrión (`-gpu host`): con renderizado por software aparecen ANR que no
  son de la aplicación.
- Un servidor de SICOT montado con Caddy (ADR-009) y su raíz instalada en el
  teléfono, como describe `INSTALACION.md`.
- El supervisor con firma electrónica asignada y un contrato con documentos.

## La lista

| # | Qué hacer | Qué tiene que pasar |
| --- | --- | --- |
| 1 | Instalar el APK encima de la versión anterior | Se instala sin desinstalar y conserva la dirección del servidor |
| 2 | Escribir una dirección `http://` en Servidor | Aviso de que Android bloquea las conexiones sin cifrar, **antes** de intentar entrar |
| 3 | Entrar con la dirección `https://` del Centro | Abre la bandeja del supervisor |
| 4 | Documentos → «Descargar» sobre un acta firmada | Se abre «Guardar como» del sistema; el PDF queda en Descargas y se abre en el visor; SICOT dice que quedó guardado |
| 5 | Registros → «Descargar registros (CSV)» | Igual que el 4, con un CSV que abre en una hoja de cálculo |
| 6 | Escribir en el campo del copiloto | El teclado **no** tapa el campo, el botón de enviar ni las sugerencias |
| 7 | Preguntar al copiloto, salir a otra aplicación un par de minutos y volver | Si la respuesta no llegó, SICOT dice que se cortó por salir de la aplicación y vuelve a preguntar solo; la respuesta aparece |
| 8 | Contrato → botón «atrás» del sistema | Vuelve a la vista anterior; en la pantalla de acceso, sale de la aplicación |
| 9 | Como Gestión: «Cargar nueva ficha» y elegir un PDF | Se abre el selector del sistema y el archivo aparece en SICOT |
| 10 | Como Administración: eliminar un formato | Diálogo de confirmación del sistema; al aceptar, el formato desaparece |
| 11 | Cerrar sesión | Vuelve a la pantalla de acceso |

Si algo de la tabla no pasa, la versión no se publica hasta saber por qué.
