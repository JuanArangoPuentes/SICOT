import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'
import { readFileSync } from 'node:fs'

// La versión que se muestra en la pantalla de acceso sale de la misma fuente que
// el instalador y el APK (tauri.conf.json). Antes ponía «SICOT v3.0» escrito a
// mano, que no correspondía a ninguna versión publicada: quien probaba la 0.5.0
// en otro teléfono no tenía cómo saber desde la aplicación qué había instalado.
const versionDeSicot: string = JSON.parse(
  readFileSync(path.resolve(import.meta.dirname, 'src-tauri/tauri.conf.json'), 'utf8'),
).version

// Los metadatos de la página (idioma, título, descripción, robots) están
// escritos en index.html y public/robots.txt. Hasta el 02-10-2026 los ponía un
// plugin heredado de Figma Make, junto con otros tres (repetición del aviso de
// error, recarga de React Refresh y una página /.figma/make/kit.html para el
// previsualizador de Figma) que servían a una plataforma ya retirada: unas 300
// líneas ajenas al proyecto, y de sus valores por defecto salía `lang="en"`.

// A dónde reenvían /api y /actuator el servidor de desarrollo y el de vista
// previa, para que la aplicación hable con la API por el MISMO origen — igual
// que hacen Caddy en producción y el nginx de la imagen (ver
// frontend/nginx.conf.template y el Caddyfile).
//
// Sin esto, el servidor de desarrollo sería el único entorno donde el frontend
// y la API viven en orígenes distintos: habría que acordarse de `VITE_API_URL`
// para levantarlo, y lo que se prueba aquí no se parecería a lo que se
// despliega. `SICOT_BACKEND` existe para el caso raro de tener el backend en
// otro puerto; `VITE_API_URL` sigue mandando en la aplicación cuando se define,
// que es lo que usan el instalador de escritorio y el APK para apuntar a un
// servidor que no es el suyo.
//
// Ojo: el proxy solo se usa si `VITE_API_URL` está definido y VACÍO
// (`VITE_API_URL=` en `.env.development`). Sin definir, el cliente cae a
// `http://localhost:8080` (services/api/client.ts) y llama al backend directo,
// que desde este equipo funciona igual, pero desde otro equipo apunta a SU
// propia máquina. La imagen de Docker sí lo deja vacío (docker-compose.yml).
const destinoDelBackend = process.env.SICOT_BACKEND || 'http://localhost:8080'

// Vite config — https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const emitSourcemaps = mode === 'development'

  return {
    base: '/',
    build: {
      sourcemap: emitSourcemaps ? 'inline' : false,
      minify: !emitSourcemaps,
    },
    plugins: [react()],
    define: {
      __VERSION_DE_SICOT__: JSON.stringify(versionDeSicot),
    },
    resolve: {
      alias: {
        '@': path.resolve(import.meta.dirname, './src'),
      },
      dedupe: ['react', 'react-dom'],
    },
    server: {
      host: '0.0.0.0',
      port: parseInt(process.env.PORT || '8443'),
      strictPort: true,
      proxy: {
        '/api': { target: destinoDelBackend },
        '/actuator': { target: destinoDelBackend },
      },
    },
    preview: {
      host: '0.0.0.0',
      port: parseInt(process.env.PORT || '8443'),
      proxy: {
        '/api': { target: destinoDelBackend },
        '/actuator': { target: destinoDelBackend },
      },
    },
    // Pruebas de componente y de servicios (Vitest). Hasta ahora el frontend
    // no tenía ninguna prueba: su única red de seguridad era `tsc --noEmit`,
    // que comprueba que los tipos cuadren pero no que la pantalla haga lo que
    // debe. `jsdom` da un DOM real para montar componentes; los `.spec.ts` de
    // Playwright viven en e2e/ y los ejecuta otra herramienta, así que se
    // excluyen explícitamente.
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.{test,spec}.{ts,tsx}'],
      exclude: ['e2e/**', 'node_modules/**'],
      css: false,
      // Cobertura CON UMBRAL, y en el CI.
      //
      // El script `test:coverage` existía desde el principio y no lo corría
      // ningún workflow: el job de frontend usa `test:run`. Es decir, se podía
      // medir y nadie medía, así que nada detectaba un archivo que entrara sin
      // una sola prueba. Y había dos: `NewUserModal.tsx` y
      // `ResetPasswordModal.tsx`, al 0 %, que son justo las dos pantallas que
      // deciden quién entra al sistema.
      //
      // Los números van UNOS PUNTOS POR DEBAJO de lo medido el 20-11-2026
      // tras añadir las pruebas que faltaban (80,7 % de línea, 71,1 % de rama;
      // antes de ellas eran 79,2 % y 69,3 %), por el mismo criterio que el umbral
      // de JaCoCo en el backend: el objetivo no es empujar la cifra hacia
      // arriba, es impedir que baje sin que nadie se entere. Un umbral pegado
      // al valor actual rompe el build en cualquier refactor legítimo y a la
      // semana alguien lo desactiva.
      //
      // SUBIRLOS ES PARTE DE SUBIR LA COBERTURA: quien añada pruebas y mueva
      // la cifra varios puntos, mueve también estas líneas.
      coverage: {
        provider: 'v8',
        reporter: ['text-summary', 'json-summary', 'html'],
        // Solo lo que es lógica de la aplicación. Se excluyen los archivos que
        // son datos o declaraciones: medirlos sube la cifra sin que nadie haya
        // probado nada, que es la forma habitual de que un umbral mienta.
        include: ['src/**/*.{ts,tsx}'],
        exclude: [
          'src/**/*.test.{ts,tsx}',
          'src/test/**',
          'src/main.tsx',
          'src/vite-env.d.ts',
          'src/types/**',
          'src/data/**',
          'src/components/icons.tsx',
          'src/services/api/types.ts',
        ],
        thresholds: {
          lines: 78,
          branches: 69,
          functions: 67,
          statements: 76,
        },
      },
    },
  }
})
