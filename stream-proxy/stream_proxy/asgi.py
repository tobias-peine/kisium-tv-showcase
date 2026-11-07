"""Production ASGI entrypoint for uvicorn workers."""

from __future__ import annotations

from .app import create_app
from .config import Settings
from .logging_setup import configure_logging

settings = Settings.from_env()
configure_logging(settings.log_level)
app = create_app(settings)
