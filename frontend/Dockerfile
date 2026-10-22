# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Giai đoạn 1: build ứng dụng React (Vite) bằng Node 20
# ---------------------------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /app

# Cài dependency trước để tận dụng cache layer khi chỉ sửa mã nguồn
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Mặc định '/api': Nginx (giai đoạn 2) proxy /api/ tới API Gateway.
# Chỉ ghi đè khi cần gọi API ở một domain khác: --build-arg VITE_API_URL=https://api.example.com/api
ARG VITE_API_URL=/api
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

# ---------------------------------------------------------------------------
# Giai đoạn 2: phục vụ file tĩnh bằng Nginx, đồng thời làm reverse proxy cho /api/
# ---------------------------------------------------------------------------
FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
