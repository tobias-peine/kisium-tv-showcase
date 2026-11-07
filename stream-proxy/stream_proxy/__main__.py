"""CLI entrypoint for the stream redirect proxy."""

from __future__ import annotations

import logging
import sys

import uvicorn

from .app import create_app
from .config import ConfigError, Settings
from .logging_setup import configure_logging

LOGGER = logging.getLogger(__name__)


def main() -> int:
    try:
        settings = Settings.from_env()
    except ConfigError as exc:
        configure_logging()
        LOGGER.error("configuration_error", extra={"error": str(exc)})
        return 2

    configure_logging()
    LOGGER.info("proxy_starting", extra=settings.safe_summary())
    app = create_app(settings)
    uvicorn.run(
        app,
        host=settings.host,
        port=settings.port,
        access_log=False,
        server_header=False,
        date_header=False,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
