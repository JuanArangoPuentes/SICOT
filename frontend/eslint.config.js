// Linter del frontend de SICOT.
//
// POR QUÉ EXISTE ESTE ARCHIVO
// ---------------------------
// Hasta el 20-11-2026 el frontend no tenía linter. Tenía `oxfmt`, que es un
// FORMATEADOR —coloca comas y sangrías, no comprueba nada— y `tsc --noEmit`,
// que comprueba que los tipos cuadren. Ninguno de los dos ve las dos familias
// de defectos que más duelen aquí:
//
//   1. REGLAS DE LOS HOOKS. `GestionPanel.tsx` tiene 33 piezas de estado en un
//      componente y `SupervisorPanel.tsx` diez `useEffect` sobre 2 175 líneas.
//      Un `useEffect` al que le falta una dependencia captura un valor viejo y
//      sigue usándolo para siempre: no es un error de tipos, no revienta, y la
//      pantalla simplemente muestra el contrato anterior. `exhaustive-deps` es
//      la única herramienta que atrapa eso leyendo el código.
//   2. ACCESIBILIDAD. SICOT es para el SENA, una entidad del Estado
//      colombiano, y la Resolución 1519 de 2020 del MinTIC obliga a WCAG 2.1
//      nivel AA. Al revisarlo había 44 elementos `<input>`, 8 `<label>` y TRES
//      usos de `htmlFor`: la mayoría de los campos del sistema no estaban
//      asociados a su etiqueta, así que un lector de pantalla no podía decir
//      qué se estaba pidiendo. No había ninguna herramienta que lo vigilara.
//
// Semgrep ya corre en el CI y está bien, pero mira seguridad: inyección,
// secretos, OWASP. No mira corrección ni accesibilidad. Son cosas distintas.
//
// CÓMO ESTÁ GRADUADO, Y POR QUÉ NO TODO ES ERROR
// ----------------------------------------------
// Un linter que se añade a un proyecto de 29 000 líneas y falla con mil avisos
// el primer día no se arregla: se desactiva. Ya hay antecedente escrito en este
// repositorio (MDL-145: «una compuerta lenta acaba quitándose»). Así que la
// separación es deliberada y tiene un criterio:
//
//   - `error`   — lo que rompe algo de verdad, o lo que ya está limpio en todo
//                 el código y hay que impedir que se ensucie.
//   - `warn`    — lo que señala deuda real y se va pagando. El CI NO falla por
//                 un `warn` (`--max-warnings` no se usa a propósito), pero
//                 quedan a la vista en cada ejecución.
//
// Lo que hoy es `warn` debería ir subiendo a `error` a medida que se limpie, y
// ese movimiento es el trabajo, no una formalidad.

import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import reactHooks from 'eslint-plugin-react-hooks'
import jsxA11y from 'eslint-plugin-jsx-a11y'
import globals from 'globals'

export default tseslint.config(
  {
    // Lo que no es código fuente nuestro. `dist` y `node_modules` son obvios;
    // `src-tauri` es Rust y su propio `target`.
    ignores: ['dist/**', 'node_modules/**', 'src-tauri/**', 'coverage/**', '*.config.js'],
  },

  js.configs.recommended,
  ...tseslint.configs.recommended,

  {
    files: ['src/**/*.{ts,tsx}', 'e2e/**/*.ts'],
    languageOptions: {
      ecmaVersion: 2023,
      globals: { ...globals.browser, ...globals.es2021 },
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    plugins: {
      'react-hooks': reactHooks,
      'jsx-a11y': jsxA11y,
    },
    rules: {
      // ── Hooks ──────────────────────────────────────────────────────────
      // Llamar un hook dentro de una condición rompe React de formas que no
      // se parecen a su causa. Nunca puede ser un aviso.
      'react-hooks/rules-of-hooks': 'error',
      // Las dependencias, aviso POR AHORA: hay efectos escritos a conciencia
      // con la lista recortada (ver los comentarios de App.tsx y
      // SupervisorPanel.tsx) y convertirlos todos de golpe es un refactor con
      // riesgo de regresión, no una limpieza. Lo que importa es que a partir
      // de hoy uno nuevo salga a la luz en cada ejecución.
      'react-hooks/exhaustive-deps': 'warn',

      // ── Accesibilidad (Resolución 1519 de 2020 · WCAG 2.1 AA) ──────────
      // Estas tres son las que cubren el hallazgo concreto: campos sin
      // etiqueta asociada. Son `error` porque el código ya está limpio
      // después de arreglarlo, y lo que hay que impedir es que vuelva.
      'jsx-a11y/label-has-associated-control': ['error', { assert: 'either' }],
      // ── Dos reglas apagadas, con el motivo ─────────────────────────────
      // `label-has-for` está DEPRECADA por el propio plugin y la reemplaza
      // `label-has-associated-control`. Exige las DOS formas de asociación a
      // la vez (anidar Y `htmlFor`), y WCAG se cumple con una: dejarla
      // encendida obligaría a inventar ids para campos que ya están bien.
      'jsx-a11y/label-has-for': 'off',
      // `control-has-associated-label` no puede ver la asociación POR
      // ANIDAMIENTO, que es el patrón de este frontend: casi todos los campos
      // van dentro de <Field label="…">, que por dentro es un <label> con el
      // control anidado. La regla mira los atributos del control y sus hijos,
      // y el texto de la etiqueta no es ninguno de los dos: es un hermano.
      //
      // Resultado medido: de sus 32 avisos, 29 eran campos correctamente
      // etiquetados. Un linter cuya mayoría de avisos es falsa se apaga en una
      // semana, y con él se habría ido lo que sí importaba — los tres campos de
      // `Registros.tsx` que de verdad no tenían etiqueta, que se arreglaron a
      // mano el 20-11-2026 con `<label htmlFor>` real.
      //
      // Lo que queda vigilando es `label-has-associated-control`, que SÍ
      // entiende el anidamiento y está en `error`.
      'jsx-a11y/control-has-associated-label': 'off',
      'jsx-a11y/alt-text': 'error',
      'jsx-a11y/aria-props': 'error',
      'jsx-a11y/aria-role': 'error',
      'jsx-a11y/role-has-required-aria-props': 'error',
      'jsx-a11y/no-redundant-roles': 'error',
      // El resto del juego recomendado, como aviso: señala deuda real
      // (controles sin rol, elementos interactivos sin teclado) que se paga
      // pantalla por pantalla. Escritas una por una a propósito: la primera
      // versión las generaba recorriendo `recommended.rules` y filtrando las
      // que ya están arriba, y ese filtro se saltó en silencio las dos que
      // tenían que quedar APAGADAS — las volvía a encender tres líneas después
      // de desactivarlas. Veintiséis líneas que se pueden leer valen más que
      // seis que hay que ejecutar mentalmente.
      'jsx-a11y/anchor-ambiguous-text': 'warn',
      'jsx-a11y/anchor-has-content': 'warn',
      'jsx-a11y/anchor-is-valid': 'warn',
      'jsx-a11y/aria-activedescendant-has-tabindex': 'warn',
      'jsx-a11y/aria-proptypes': 'warn',
      'jsx-a11y/aria-unsupported-elements': 'warn',
      'jsx-a11y/autocomplete-valid': 'warn',
      'jsx-a11y/click-events-have-key-events': 'warn',
      'jsx-a11y/heading-has-content': 'warn',
      'jsx-a11y/html-has-lang': 'warn',
      'jsx-a11y/iframe-has-title': 'warn',
      'jsx-a11y/img-redundant-alt': 'warn',
      'jsx-a11y/interactive-supports-focus': 'warn',
      'jsx-a11y/media-has-caption': 'warn',
      'jsx-a11y/mouse-events-have-key-events': 'warn',
      'jsx-a11y/no-access-key': 'warn',
      'jsx-a11y/no-autofocus': 'warn',
      'jsx-a11y/no-distracting-elements': 'warn',
      'jsx-a11y/no-interactive-element-to-noninteractive-role': 'warn',
      'jsx-a11y/no-noninteractive-element-interactions': 'warn',
      'jsx-a11y/no-noninteractive-element-to-interactive-role': 'warn',
      'jsx-a11y/no-noninteractive-tabindex': 'warn',
      'jsx-a11y/no-static-element-interactions': 'warn',
      'jsx-a11y/role-supports-aria-props': 'warn',
      'jsx-a11y/scope': 'warn',
      'jsx-a11y/tabindex-no-positive': 'warn',

      // ── TypeScript ─────────────────────────────────────────────────────
      // `any` explícito como aviso: hay unos pocos en los límites con el
      // webview de Tauri, donde el tipo real lo decide la plataforma.
      '@typescript-eslint/no-explicit-any': 'warn',
      // Una variable sin usar suele ser un resto de un refactor a medias. Se
      // admite el prefijo `_` para lo que se descarta a propósito (el segundo
      // argumento de un `map`, un parámetro que la firma exige).
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrors: 'none' },
      ],
      // `catch {}` vacío es un patrón DELIBERADO y muy usado aquí: el
      // almacenamiento del navegador puede estar bloqueado (ventana privada,
      // políticas del equipo) y la aplicación tiene que seguir en pie. Cada
      // uno de esos bloques lleva su comentario explicando qué se ignora y
      // por qué, así que la regla estorbaría en vez de ayudar.
      'no-empty': ['error', { allowEmptyCatch: true }],
    },
  },

  {
    // Las pruebas corren en Node (vitest, playwright) y usan sus globales.
    files: ['**/*.test.{ts,tsx}', 'src/test/**/*.ts', 'e2e/**/*.ts', 'vite.config.ts'],
    languageOptions: { globals: { ...globals.node, ...globals.vitest } },
    rules: {
      // En una prueba, un `any` para construir una respuesta falsa es más
      // legible que reconstruir el tipo entero del backend.
      '@typescript-eslint/no-explicit-any': 'off',
    },
  },
)
