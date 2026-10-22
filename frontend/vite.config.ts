import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    // Bundle chính gồm React, React Router và Ant Design (~900 kB, gzip ~290 kB) nên nâng ngưỡng
    // cảnh báo mặc định 500 kB. Recharts đã được tách riêng nhờ lazy load trang Dashboard.
    chunkSizeWarningLimit: 1000,
  },
  server: {
    port: 5173,
    proxy: {
      // Khi chạy `npm run dev`, chuyển tiếp mọi request /api tới API Gateway (cổng 8080)
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
