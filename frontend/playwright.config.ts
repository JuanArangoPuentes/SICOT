import { defineConfig, devices } from "@playwright/test"

// Pruebas E2E reales contra la UI servida por Vite. Requieren el backend +
// PostgreSQL corriendo aparte (con los usuarios de perfil "dev" ya sembrados
// — ver backend/README.md) porque Playwright solo levanta el frontend; no
// hay nada que mockear salvo lo que explícitamente se intercepte con
// page.route() (los specs bajo e2e/specs/ai/, que no dependen de Ollama).
export default defineConfig({
  testDir: "./e2e/specs",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: "html",
  use: {
    baseURL: process.env.E2E_BASE_URL || "http://localhost:8443",
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "npm run dev",
    url: process.env.E2E_BASE_URL || "http://localhost:8443",
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
})
