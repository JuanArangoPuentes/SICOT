// Configuración de ESLint del frontend.
//
// Existe por MDL-151: el frontend no tenía ningún linter, y por eso la
// accesibilidad que exige la Resolución 1519 de 2020 no se podía medir. Su
// única red de seguridad eran `tsc --noEmit` y oxfmt, y ninguno de los dos
// mira si un campo de formulario tiene etiqueta.
//
// Dos decisiones que no son obvias:
//
//  1. **No hay reglas de estilo.** Del formato se encarga oxfmt con
//     `.oxfmtrc.json`, y meter aquí reglas de formato significaría reformatear
//     el repositorio entero: cualquier `git blame` futuro pasaría por ese
//     commit en vez de por el que de verdad escribió la línea. Aquí solo hay
//     reglas de error real y de accesibilidad.
//
//  2. **ESLint está fijado a la línea 9, no a la 10.** `eslint-plugin-jsx-a11y`
//     6.10.2 declara soporte hasta `eslint@^9`, y con la 10 npm no resuelve el
//     árbol. El plugin es el motivo de que este archivo exista, así que manda
//     él. Cuando publique soporte para la 10, se sube.

import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import react from 'eslint-plugin-react'
import reactHooks from 'eslint-plugin-react-hooks'
import jsxA11y from 'eslint-plugin-jsx-a11y'
import globals from 'globals'

export default tseslint.config(
  {
    // Nada de esto lo escribimos nosotros: salida de compilación, informes de
    // Playwright, cobertura y el árbol de Rust de Tauri.
    ignores: ['dist/**', 'coverage/**', 'playwright-report/**', 'test-results/**', 'src-tauri/**', 'node_modules/**'],
  },

  js.configs.recommended,
  tseslint.configs.recommended,

  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: { ...globals.browser },
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    settings: { react: { version: 'detect' } },
    plugins: {
      react,
      'react-hooks': reactHooks,
      'jsx-a11y': jsxA11y,
    },
    rules: {
      ...react.configs.flat.recommended.rules,
      ...react.configs.flat['jsx-runtime'].rules,
      ...reactHooks.configs.recommended.rules,
      ...jsxA11y.flatConfigs.recommended.rules,

      // El contrato de la Resolución 1519 que de verdad se incumplía: 48
      // `<input>` y 3 `htmlFor` en todo `src/`. Se suben a error para que el
      // CI los bloquee, no solo los avise.
      'jsx-a11y/label-has-associated-control': ['error', { assert: 'either', depth: 3 }],

      // Un `any` suelto es deuda, no un error que rompa el arranque: queda
      // como aviso para que el CI no se cierre por él, pero se vea.
      '@typescript-eslint/no-explicit-any': 'warn',

      // `console.debug` sí está permitido, y no por comodidad: es el canal que
      // el frontend usa deliberadamente para los fallos de refresco en
      // segundo plano, los que no deben tocar la pantalla —«la lista buena
      // sigue ahí»—. Es justo lo contrario de un `console.log` olvidado.
      'no-console': ['warn', { allow: ['warn', 'error', 'debug'] }],
      'react/prop-types': 'off', // los tipos los pone TypeScript

      // Las tres reglas que vienen del React Compiler, apagadas a propósito.
      // No marcan fallos de este código: marcan tres patrones que el
      // repositorio usa a conciencia y tiene explicados junto al código —
      // cargar datos al montar con setState dentro del efecto, guardar la
      // última versión de un callback en una referencia, y calcular con
      // `Date.now()` los días que le quedan a un contrato. Eran 18 avisos,
      // todos de esa clase, ninguno un defecto. Dejarlos encendidos habría
      // enseñado al equipo a ignorar el linter el primer día, que es peor que
      // no tenerlo (el mismo razonamiento que el job de vulnerabilidades del
      // CI ya tiene escrito). Adoptar el React Compiler es una decisión de
      // arquitectura con su propio issue, no un efecto colateral de añadir un
      // linter.
      'react-hooks/set-state-in-effect': 'off',
      'react-hooks/purity': 'off',
      'react-hooks/refs': 'off',
    },
  },

  {
    // Pruebas: corren en Node y en jsdom, y usan los globales de Vitest.
    files: ['**/*.test.{ts,tsx}', 'src/test/**', 'e2e/**', '*.config.{ts,js}'],
    languageOptions: {
      globals: { ...globals.node, ...globals.browser },
    },
    rules: {
      'no-console': 'off',
      '@typescript-eslint/no-explicit-any': 'off',
    },
  },
)
