## Qué cambia

<!-- Una o dos frases. El detalle está en el diff. -->

## Por qué

<!-- El problema que resuelve. Si corrige un fallo, describa el síntoma que
     veía el usuario, no solo la causa técnica. -->

## Cómo se verificó

<!-- La parte que más importa. "Pasa el CI" no es verificación de que la
     funcionalidad haga lo que debe: el CI comprueba que nada se rompió.

     Diga qué ejecutó y qué observó. Por ejemplo:
       - Levanté el stack y firmé un documento; la verificación devolvió ÍNTEGRO.
       - Alteré el contenido en la base y volvió a decir ALTERADO.
       - Añadí la prueba que habría detectado este fallo (X.java:42). -->

## Comprobaciones

- [ ] `./mvnw verify` en verde (si tocó el backend)
- [ ] `npm run test:run` y `npm run typecheck` en verde (si tocó el frontend)
- [ ] Si corrige un fallo, hay una prueba que lo habría detectado
- [ ] Si cambia el esquema, es una migración **nueva** (nunca se edita una ya aplicada)
- [ ] Si toma una decisión no obvia, está explicada junto al código o en un ADR
