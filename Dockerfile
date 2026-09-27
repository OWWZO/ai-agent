# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-21 AS backend-build

WORKDIR /workspace
COPY . .
RUN mvn -B -pl Reactor-agent-app -am package \
    -DskipTests \
    -Dmaven.test.skip=true

FROM node:22-bookworm-slim AS opencli-build

WORKDIR /opt/opencli
COPY adapter-host/package.json adapter-host/package-lock.json ./
COPY adapter-host/scripts ./scripts
RUN npm ci
COPY adapter-host/ ./
RUN npm run build && npm prune --omit=dev

FROM eclipse-temurin:21-jre AS backend

WORKDIR /app
COPY --from=backend-build /workspace/Reactor-agent-app/target/Reactor-agent-app.jar /app/app.jar
COPY runtime/skills /app/runtime/skills
COPY --from=node:22-bookworm-slim /usr/local /usr/local
COPY --from=opencli-build /opt/opencli/dist /opt/opencli/dist
COPY --from=opencli-build /opt/opencli/clis /opt/opencli/clis
COPY --from=opencli-build /opt/opencli/cli-manifest.json /opt/opencli/cli-manifest.json
COPY --from=opencli-build /opt/opencli/package.json /opt/opencli/package.json
COPY --from=opencli-build /opt/opencli/node_modules /opt/opencli/node_modules
RUN mkdir -p /app/data/log /data/skilloutput
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-pip \
    && python3 -m pip install --no-cache-dir --break-system-packages \
        twitter-cli==0.8.6 rdt-cli==0.4.1 \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai" \
    REACTOR_OPENCLI_COMMAND=node \
    REACTOR_OPENCLI_MAIN=/opt/opencli/dist/src/main.js
EXPOSE 8100

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"]

FROM python:3.11-slim-bookworm AS reactor-tool

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy

RUN sed -i 's|deb.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources \
    && apt-get update -o Acquire::Retries=5 \
    && apt-get install -y -o Acquire::Retries=5 --no-install-recommends poppler-utils \
    && rm -rf /var/lib/apt/lists/* \
    && pip install --no-cache-dir uv

WORKDIR /app
COPY reactor-tool/ /app/
RUN uv sync --frozen --no-dev --no-cache \
    && chmod +x /app/start.sh /app/start-split.sh \
    && mkdir -p /data/skilloutput /data/logs

ENV PATH="/app/.venv/bin:${PATH}" \
    ENV=prod
EXPOSE 1601 1602

ENTRYPOINT ["/app/start.sh"]

FROM node:22-alpine AS ui-build

WORKDIR /workspace/ui
RUN corepack enable && corepack prepare pnpm@10.12.4 --activate
COPY ui/package.json ui/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY ui/ ./

ARG SERVICE_BASE_URL=
ARG REACTOR_TOOL_BASE_URL=/tool
ARG VITE_Mrag_TOOL_URL=/tool
ENV SERVICE_BASE_URL=${SERVICE_BASE_URL} \
    REACTOR_TOOL_BASE_URL=${REACTOR_TOOL_BASE_URL} \
    VITE_Mrag_TOOL_URL=${VITE_Mrag_TOOL_URL}

RUN pnpm build

FROM nginx:1.27-alpine AS frontend

COPY --from=ui-build /workspace/ui/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
