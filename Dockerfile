# Bannerfall production image (docs/plan/01-architecture.md §8).
#   docker build -t bannerfall:1.2.0 --build-arg VERSION=1.2.0 .
# Three stages: the web app, the server jar (with the web build inside), a small runtime.

# --- 1. web: pnpm build -> /web/dist ---
FROM node:24-slim AS web
WORKDIR /web
# The pnpm version comes from "packageManager" in web/package.json.
RUN corepack enable
COPY web/package.json web/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY web/ ./
RUN pnpm build

# --- 2. server: Gradle bootJar with the web build served from static/ ---
FROM eclipse-temurin:25-jdk AS server
WORKDIR /build
# Wrapper and build files first: the Gradle download and the dependencies stay cached until they change.
COPY server/gradlew server/settings.gradle.kts server/build.gradle.kts ./
COPY server/gradle gradle
RUN ./gradlew --no-daemon --version
COPY server/src src
COPY --from=web /web/dist src/main/resources/static
ARG VERSION=0.0.1-SNAPSHOT
RUN ./gradlew --no-daemon bootJar -PappVersion=${VERSION}

# --- 3. runtime: JRE only, non-root ---
FROM eclipse-temurin:25-jre
RUN groupadd --system --gid 10001 bannerfall \
 && useradd --system --uid 10001 --gid bannerfall --home-dir /app --shell /usr/sbin/nologin bannerfall \
 && mkdir -p /app/logs \
 && chown -R bannerfall:bannerfall /app
WORKDIR /app
COPY --from=server --chown=bannerfall:bannerfall /build/build/libs/bannerfall.jar /app/app.jar
# The host console (docs/plan/08-host-console.md): docker compose exec app bannerfall-admin
COPY --chmod=755 ops/bannerfall-admin /usr/local/bin/bannerfall-admin
USER bannerfall
# Heap at most 60 % of the container's memory (768m in compose.prod.yaml): metaspace, threads,
# direct buffers and the console's own JVM (docker compose exec app bannerfall-admin) need the rest.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60
EXPOSE 8080
# The JRE image has no curl: a plain TCP request from bash.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3 && grep -q "\"status\":\"UP\"" <&3'
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
