FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY index.html vite.config.js ./
COPY public/ public/
COPY src/ src/
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.31-alpine
ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="lopon-frontend" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.source="https://github.com/Wagonee/t_sre_lopon_fit_app"
ENV PORT=8080 \
    BACKEND_HOST=backend \
    BACKEND_PORT=8080 \
    NGINX_RESOLVER=127.0.0.11 \
    NGINX_MAX_BODY_SIZE=25m \
    TRUSTED_PROXY_CIDR=127.0.0.1/32
COPY nginx/proxy.conf nginx/security-headers.conf /etc/nginx/snippets/
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 8080
