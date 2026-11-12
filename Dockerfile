# NOT TESTED: written without a Docker daemon available. Build: docker build -t j2me .
# Run:   docker run -p 8080:8080 -v j2me-games:/app/games -v j2me-data:/app/data j2me
FROM node:22-bookworm-slim AS web
WORKDIR /app/web
COPY web/package*.json ./
RUN npm ci
COPY web ./
RUN npm run build

FROM eclipse-temurin:21-jdk-jammy AS worker
WORKDIR /app/worker
COPY worker ./
RUN chmod +x build.sh && ./build.sh

FROM eclipse-temurin:21-jre-jammy
# fontconfig + a font: headless AWT needs them to draw text
RUN apt-get update && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
COPY --from=node:22-bookworm-slim /usr/local/bin/node /usr/local/bin/node
WORKDIR /app
COPY gateway/package*.json ./gateway/
RUN cd gateway && npm ci --omit=dev --ignore-scripts 2>/dev/null || true
COPY --from=node:22-bookworm-slim /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/npm
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && cd gateway && npm ci --omit=dev
COPY gateway ./gateway
COPY --from=worker /app/worker/build ./worker/build
COPY worker/vendor/microemu/lib ./worker/vendor/microemu/lib
COPY --from=web /app/web/dist ./web/dist
RUN mkdir -p games data
ENV PORT=8080
EXPOSE 8080
WORKDIR /app/gateway
CMD ["node", "server.js"]
