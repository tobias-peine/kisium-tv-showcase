# syntax=docker/dockerfile:1
FROM python:3.12-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --no-create-home app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY stream_proxy ./stream_proxy
RUN chmod -R a+rX /app

USER app

FROM base AS test
USER root
COPY requirements-dev.txt ./
RUN pip install --no-cache-dir -r requirements-dev.txt
COPY tests ./tests
USER app
CMD ["python", "-m", "pytest", "-q"]

FROM base AS runtime
EXPOSE 5000
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD ["python", "-c", "import os,urllib.request; urllib.request.urlopen(f'http://127.0.0.1:{os.getenv(\"PROXY_PORT\",\"5000\")}/healthz', timeout=3)"]
# Production ASGI server. Access log is disabled because the Xtream path
# contains credentials; structured application logs are emitted to stdout.
ENTRYPOINT ["sh", "-c"]
CMD ["exec uvicorn stream_proxy.asgi:app --host \"${PROXY_HOST:-0.0.0.0}\" --port \"${PROXY_PORT:-5000}\" --workers \"${WEB_CONCURRENCY:-2}\" --no-access-log --proxy-headers --forwarded-allow-ips \"${FORWARDED_ALLOW_IPS:-127.0.0.1}\" --log-level \"${UVICORN_LOG_LEVEL:-info}\" --no-server-header"]
