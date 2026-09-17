import { defineConfig, devices } from '@playwright/test'

// Pruebas E2E reales contra la UI servida por Vite. Requieren el backend +
// PostgreSQL corriendo aparte (con los usuarios de perfil "dev" ya sembrados
// — ver backend/README.md) porque Playwright solo levanta el frontend; no
// hay nada que mockear salvo lo que explícitamente se intercepte con
// page.route() (los specs bajo e2e/specs/ai/, que no dependen de Ollama).
export default defineConfig({
  testDir: './e2e/specs',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: 'html',
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:8443',
    trace: 'on-first-retry',
  },
  // Dos proyectos, no uno duplicado. El de escritorio corre todo menos las
  // pruebas de armazón móvil; el móvil corre solo esas, con un teléfono de
  // verdad emulado (agente de usuario de Android, eventos táctiles, 393 px).
  //
  // Existe porque el trabajo de adaptación a pantalla estrecha se deshace solo
  // con que alguien escriba la siguiente pantalla como se escribieron las
  // anteriores —con rejillas de columnas fijas— y nadie se enteraría hasta
  // abrirla en un teléfono. La auditoría del 16 de septiembre de 2026
  // (docs/AUDITORIA_MOVIL_2026-09-16.md) midió 41 elementos recortados en una
  // sola pantalla sin que ninguna compuerta dijera nada.
  //
  // No se borre por parecer un duplicado del de arriba: lo que cambia no es el
  // navegador sino el tamaño, que es justo lo que se está comprobando.
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      testIgnore: /specs[\\/]movil[\\/]/,
    },
    {
      name: 'movil',
      use: { ...devices['Pixel 7'] },
      testMatch: /specs[\\/]movil[\\/].*\.spec\.ts/,
    },
  ],
  webServer: {
    command: 'npm run dev',
    url: process.env.E2E_BASE_URL || 'http://localhost:8443',
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
})
