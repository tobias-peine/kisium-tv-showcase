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

COPY catalog_sync ./catalog_sync
RUN chmod -R a+rX /app \
    && mkdir -p /data \
    && chown -R app:app /app /data

USER app

FROM base AS test
USER root
COPY requirements-dev.txt ./
RUN pip install --no-cache-dir -r requirements-dev.txt
COPY tests ./tests
USER app
CMD ["python", "-m", "pytest", "-q"]

FROM base AS runtime
VOLUME ["/data"]
HEALTHCHECK --interval=5m --timeout=10s --start-period=2m --retries=3 \
    CMD ["python", "-m", "catalog_sync", "--healthcheck"]
ENTRYPOINT ["python", "-m", "catalog_sync"]
CMD ["--loop"]
