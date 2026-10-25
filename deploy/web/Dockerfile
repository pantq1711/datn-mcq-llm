# syntax=docker/dockerfile:1
# Frontend (Vite build tĩnh) + Caddy. Build context: GỐC repo (cần cả front_end/ và deploy/web/):
#   docker build -f deploy/web/Dockerfile -t datn-web .

# ---- Tầng build: Node ----
FROM node:22-alpine AS build
WORKDIR /web
COPY front_end/package.json front_end/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY front_end/ ./
# Địa chỉ API được ghi cứng vào bản build (import.meta.env.VITE_API_BASE_URL). Mặc định "/api" vì frontend và API
# cùng domain qua Caddy; phải thống nhất với cách frontend viết đường dẫn gọi API.
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

# ---- Tầng chạy: Caddy phục vụ dist + proxy /api ----
FROM caddy:2.11-alpine
COPY deploy/web/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /web/dist /srv
