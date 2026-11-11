# SICOT — qué pasa cuando algo se cae

Esto describe lo que hace el sistema por su cuenta cuando una de sus piezas deja
de funcionar: cómo se entera, qué intenta, a quién avisa y qué ve mientras tanto
la persona que estaba trabajando.

## Por qué existe esto

Por una lección con fecha. El **11-11-2026** se revisó el servidor del Centro y
llevaba **seis días sin un solo respaldo**. Lo más incómodo del hallazgo no fue
el respaldo: fue que **el sistema ya lo había detectado**. `VigilanciaDelRespaldo`
había escrito en el log, con todas las letras y el comando exacto a comprobar:

```
RESPALDO: el más reciente tiene 119 h de antigüedad y el RPO
comprometido en ADR-002 es de 24 h.
```

Dos veces. Y no sirvió de nada, porque nadie lee un log mientras todo parece
funcionar. **Un aviso que se queda en el log es un aviso que no existe.**

Lo mismo pasaba con la IA, de otra forma: cuando Ollama se caía, lo sabía
únicamente la llamada que lo tropezó. El supervisor pulsaba «Redactar con el
asistente», esperaba y recibía un error. Lo intentaba otra vez. Nada le decía que
el problema no era su consulta, que insistir no iba a servir, ni que alguien
estuviera avisado — porque nadie lo estaba.

## Las cuatro cosas que ahora ocurren solas

### 1. Los componentes se lo cuentan entre ellos

Cada pieza que puede fallar avisa por `AvisoDeSalud`, y `SaludDelSistema` lleva
la cuenta. Hoy avisan `OllamaClient` (la IA) y `VigilanciaDelRespaldo`.

Los estados son tres: **operativo**, **degradado** (falló, se está intentando) y
**caído**. Cuántos fallos seguidos hacen falta para pasar a caído lo decide cada
servicio, y no es el mismo número para todos:

| Servicio | Fallos para darlo por caído | Por qué |
|---|---|---|
| IA | 3 | Una llamada lenta no es una caída. El equipo puede estar ocupado. |
| Respaldo | 1 | «El más reciente tiene 136 h» se sabe de cierto a la primera mirada. |

### 2. Se intenta arreglar antes de molestar a nadie

`ReparadorDeServicios` corre cada dos minutos, y **solo si hay algo abierto**.
Para la IA, el intento es pedirle a Ollama que cargue el modelo, porque la causa
más común de que falle aquí no es que esté roto: es que descargó el modelo de
memoria y la primera pregunta después de un rato paga la carga entera.

Lo que **no** hace es reiniciar contenedores. Eso exigiría darle al backend el
socket de Docker, y eso es darle el equipo entero: cualquier fallo de seguridad
en la aplicación pasaría a ser control total del servidor.

### 3. Si no se pudo, sale un informe para el técnico

Por tres sitios a la vez, y cada uno cubre un caso distinto:

- **El log**, siempre. Para quien ya está diagnosticando.
- **Un archivo**, uno por incidencia, en `SICOT_INFORMES_DIRECTORIO`
  (`/informes` en producción, un volumen). En volumen y no en el contenedor
  porque el despliegue automático lo recrea en cada versión nueva: el 11-11 no se
  pudo saber si la vigilancia había avisado entre el 6 y el 9 de noviembre porque
  al recrear el contenedor se perdió todo lo anterior.
- **Un correo** a `SICOT_TECNICO_EMAIL`, que es el único de los tres que va a
  buscar a la persona en vez de esperar a que mire.

Para leer los archivos:

```bash
docker exec sicot-backend ls -lt /informes
docker exec sicot-backend cat /informes/<archivo>
```

El informe trae qué falló, desde cuándo, cuántas veces, lo que dijo el fallo tal
cual, qué puede seguir haciendo el usuario y **los comandos exactos** a
comprobar. Eso último es a propósito: un aviso que obliga a buscar el comando en
la documentación se atiende más tarde, y a veces nunca.

Dos reglas que lo mantienen legible:

- **Un informe por incidencia, no por fallo.** Un servicio que falla cada diez
  segundos mandaría cien informes, y el técnico dejaría de leerlos.
- **Cuando vuelve, se avisa también.** Para que nadie salga a buscar un fallo que
  ya se arregló solo.

### 4. A quien está trabajando se le dice lo que le sirve

El frontend pregunta cada minuto por `GET /api/salud` y pinta una franja con el
mensaje. El texto no se redacta en el frontend: vive en `Servicio`, al lado de lo
que falla, para que quien añada un servicio nuevo tenga que decidir en ese
momento qué ve el usuario.

Y los mensajes dicen **qué sí se puede seguir haciendo**. SICOT funciona sin el
modelo de IA, así que un aviso que solo dijera «la IA no está disponible» dejaría
al supervisor creyendo que no puede trabajar:

> El asistente de IA no está disponible. Puede seguir trabajando con normalidad:
> los pasos, los documentos y las firmas no dependen de él. El técnico ya fue
> avisado.

No todo se le cuenta. El respaldo caído no cambia nada de lo que el usuario puede
hacer, así que no se le enseña: un aviso que no le sirve de nada le enseña a
ignorar los avisos.

La franja **desaparece sola** en cuanto el servicio vuelve. Por eso se pregunta
cada minuto y no una sola vez al abrir.

## Lo que hay que configurar

| Variable | Qué pasa si falta |
|---|---|
| `SICOT_TECNICO_EMAIL` | El informe se queda en el log y en el archivo, esperando a que alguien mire. Es exactamente el fallo del 11-11. |
| `SICOT_INFORMES_DIRECTORIO` | No se escribe archivo. En producción lo pone `docker-compose.prod.yml`. |

Cuando lo que falla es **el correo**, el informe no se manda por correo: se queda
en el log y en el archivo, por el mismo motivo por el que no se telefonea para
avisar de que el teléfono no funciona.

## Por qué no se usa `/actuator/health` para esto

Porque dice otra cosa. El actuator responde si la aplicación está sana, y la
aplicación está perfectamente sana con Ollama caído: por diseño, SICOT funciona
sin el modelo. Si la IA tirara el health, el despliegue automático daría el
arranque por fallido y volvería a la versión anterior sin motivo, y el túnel de
Cloudflare dejaría de mandar tráfico a un servidor que funciona.

`GET /api/salud` es lo contrario: la aplicación está bien y, aun así, hay algo
que contarle a quien la usa. Devuelve lista vacía cuando todo va bien.

## Métricas

- `sicot.salud.estado{servicio}` — 0 operativo, 1 degradado, 2 caído.
- `sicot.respaldo.antiguedad.horas` — −1 si no se pudo determinar.

La señal tiene que existir también donde no la lee una persona.
