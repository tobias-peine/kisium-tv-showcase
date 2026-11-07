"""CLI entrypoint for the Kisium Netzkino VOD catalog sync."""

from __future__ import annotations

import argparse
import logging
import os
import signal
import sys
import threading

from dotenv import load_dotenv

from .config import ConfigError, Settings
from .http import JsonApiClient
from .logging_setup import configure_logging
from .service import CatalogSyncService, InstanceLock
from .source import KisiumCatalogSource
from .storage import CatalogStorage
from .netzkino import NetzkinoParser

LOGGER = logging.getLogger(__name__)


def _healthcheck() -> int:
    """Check freshness without requiring feed credentials."""
    output_dir = os.getenv("OUTPUT_DIR", "/data")
    output_format = os.getenv("OUTPUT_FORMAT", "both").lower()
    json_filename = os.getenv("JSON_FILENAME", "catalog.json")
    sqlite_filename = os.getenv("SQLITE_FILENAME", "catalog.sqlite")
    try:
        interval = int(os.getenv("SYNC_INTERVAL_SECONDS", "21600"))
    except ValueError:
        interval = 21600
    storage = CatalogStorage(
        output_dir=output_dir,
        output_format=output_format if output_format in {"json", "sqlite", "both"} else "both",
        json_filename=json_filename,
        sqlite_filename=sqlite_filename,
    )
    return 0 if storage.healthcheck(max(interval * 2, 3600)) else 1


def _parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="kisium-catalog-sync",
        description="Synchronisiert den freien Netzkino-VOD-Katalog für Kisium TV.",
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--loop", action="store_true", help="Dauerbetrieb (Container-Default).")
    mode.add_argument("--once", action="store_true", help="Einmal synchronisieren und beenden.")
    mode.add_argument("--healthcheck", action="store_true", help="Nur Datenfrische prüfen.")
    parser.add_argument("--log-level", default=None, help="DEBUG, INFO, WARNING, ERROR")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    load_dotenv()
    args = _parse_args(argv)
    if args.healthcheck:
        return _healthcheck()

    try:
        settings = Settings.from_env()
    except ConfigError as exc:
        configure_logging((args.log_level or os.getenv("LOG_LEVEL", "INFO")).upper())
        LOGGER.error("configuration_error", extra={"error": str(exc)})
        return 2

    configure_logging(args.log_level or settings.log_level)
    LOGGER.info("sync_starting", extra=settings.safe_summary())

    lock = InstanceLock(settings.output_dir)
    if not lock.acquire():
        LOGGER.error("another_sync_instance_is_running")
        return 3

    storage = CatalogStorage(
        output_dir=settings.output_dir,
        output_format=settings.output_format,
        json_filename=settings.json_filename,
        sqlite_filename=settings.sqlite_filename,
    )
    http_client = JsonApiClient(settings)
    parser = NetzkinoParser()
    source = KisiumCatalogSource(
        client=http_client,
        parser=parser,
        feed_url=settings.feed_url,
        source_id=settings.source_id,
        page_size=settings.page_size,
        fetch_page_size=settings.fetch_page_size,
        max_pages=settings.max_pages,
    )
    service = CatalogSyncService(
        source=source,
        storage=storage,
        source_id=settings.source_id,
        interval_seconds=settings.sync_interval_seconds,
    )

    stop_event = threading.Event()

    def _stop(_signum: int, _frame: object) -> None:
        LOGGER.info("shutdown_requested")
        stop_event.set()

    signal.signal(signal.SIGTERM, _stop)
    signal.signal(signal.SIGINT, _stop)

    try:
        if args.once:
            try:
                service.run_once()
                return 0
            except Exception:
                return 1
        service.run_forever(stop_event)
        return 0
    finally:
        http_client.close()
        lock.release()


if __name__ == "__main__":
    sys.exit(main())
