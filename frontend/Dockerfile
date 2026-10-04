# syntax=docker/dockerfile:1
# Multi-stage: Vite builds the static SPA, nginx serves it and proxies the API on the SAME origin
# (so the HttpOnly cookie works without CORS — decision D6) (Phase 6.4).

FROM node:24-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/proxy-params.inc /etc/nginx/conf.d/proxy-params.inc
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --retries=3 CMD wget -qO- http://127.0.0.1/healthz || exit 1
