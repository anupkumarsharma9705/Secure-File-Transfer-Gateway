# ---- Stage 1: Build ----
FROM node:20.19-alpine AS build
WORKDIR /app

ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL

COPY package*.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---- Stage 2: Serve (non-root, unprivileged nginx on port 8080) ----
# nginxinc/nginx-unprivileged is purpose-built to run as a non-root user
# (UID 101) and listens on 8080, so the container never needs to bind a
# privileged port or run as root.
FROM nginxinc/nginx-unprivileged:1.31-alpine

# Replace the default server block with the SFTG config.
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

USER nginx
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/ >/dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]
