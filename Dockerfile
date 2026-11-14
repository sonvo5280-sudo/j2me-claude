FROM node:22-bookworm-slim AS web
WORKDIR /app/web
COPY web/package*.json ./
RUN npm ci
COPY web ./
RUN npm run build

FROM node:22-bookworm-slim AS gateway-deps
WORKDIR /app/gateway
COPY gateway/package*.json ./
RUN npm ci --omit=dev

FROM eclipse-temurin:21-jdk-jammy AS worker
WORKDIR /app/worker
COPY worker ./
RUN chmod +x build.sh && ./build.sh

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core libfreetype6 \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --uid 10001 app
COPY --from=node:22-bookworm-slim /usr/local/bin/node /usr/local/bin/node
WORKDIR /app
COPY --from=gateway-deps /app/gateway/node_modules ./gateway/node_modules
COPY gateway ./gateway
COPY --from=worker /app/worker/build ./worker/build
COPY worker/vendor/microemu/lib ./worker/vendor/microemu/lib
COPY --from=web /app/web/dist ./web/dist
RUN mkdir -p games data && chown -R app:app games data
USER app
ENV PORT=8080 NODE_ENV=production
EXPOSE 8080
WORKDIR /app/gateway
CMD ["node", "server.js"]
