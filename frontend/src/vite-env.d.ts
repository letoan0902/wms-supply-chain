/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** URL gốc của API. Mặc định là '/api' (được proxy tới API Gateway). */
  readonly VITE_API_URL?: string
  /** Endpoint SockJS/STOMP nhận thông báo thời gian thực. Mặc định là '/ws-wms' (cùng origin, được proxy). */
  readonly VITE_WS_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
