# ── 빌드 스테이지: Vite 정적 빌드 ──
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ── 서빙 스테이지: nginx 정적 서빙 + /api 프록시 (/mcp는 노출하지 않음) ──
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
