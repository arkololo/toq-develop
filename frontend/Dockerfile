# Build context: repository root.
FROM node:22.23.2-alpine@sha256:b6f26b36c8ff49624cfdac716b8ea1138d606df02586a77d364bb5536a634f85 AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
ARG VITE_KEYCLOAK_URL=http://localhost:8081
ENV VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL
COPY infra/reverse-proxy/default.conf /app/nginx.template
COPY scripts/render-edge-config.mjs /app/render-edge-config.mjs
RUN node render-edge-config.mjs nginx.template nginx.conf && npm run build

FROM nginxinc/nginx-unprivileged:1.30.5-alpine@sha256:04a3275f25d766cff8926d2e57b2ff34a783d6b12a702dc98bb82226d2d9a508 AS runtime
COPY --from=build /app/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
