/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** URL gốc của API. Mặc định là '/api' (được proxy tới API Gateway). */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
