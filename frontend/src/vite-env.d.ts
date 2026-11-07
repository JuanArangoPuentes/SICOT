/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

/** Versión de tauri.conf.json, inyectada por vite.config.ts al compilar. */
declare const __VERSION_DE_SICOT__: string
