"""Sync orchestration and the long-running container loop."""

from __future__ import annotations

import fcntl
import logging
import random
import re
import time
from pathlib import Path
from typing import Any

from .source import KisiumCatalogSource
from .storage import CatalogStorage

LOGGER = logging.getLogger(__name__)


class InstanceLock:
    """Prevents two sync containers from writing the same volume."""

    def __init__(self, output_dir: str | Path) -> None:
        self.path = Path(output_dir) / ".catalog-sync.lock"
        self._handle: Any = None

    def acquire(self) -> bool:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._handle = open(self.path, "a+", encoding="utf-8")
        try:
            fcntl.flock(self._handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            self._handle.close()
            self._handle = None
            return False
        return True

    def release(self) -> None:
        if self._handle is not None:
            try:
                fcntl.flock(self._handle.fileno(), fcntl.LOCK_UN)
            finally:
                self._handle.close()
                self._handle = None


class CatalogSyncService:
    def __init__(
        self,
        source: KisiumCatalogSource,
        storage: CatalogStorage,
        source_id: str,
        interval_seconds: int,
    ) -> None:
        self._source = source
        self._storage = storage
        self._source_id = source_id
        self._interval_seconds = max(60, interval_seconds)

    def run_once(self) -> int:
        started_at = _utc_now()
        try:
            movies = self._source.get_all_movies(force_refresh=True)
            payload = self._source.format_for_kisium(movies)
            finished_at = _utc_now()
            self._storage.write(
                payload=payload,
                movies=movies,
                source_id=self._source_id,
                started_at=started_at,
                finished_at=finished_at,
            )
            LOGGER.info(
                "sync_completed",
                extra={"movie_count": len(movies), "source_id": self._source_id},
            )
            return len(movies)
        except Exception as exc:
            safe_error = _safe_error(exc)
            LOGGER.error(
                "sync_failed",
                extra={"error": safe_error, "source_id": self._source_id},
            )
            self._storage.record_failure(self._source_id, started_at, safe_error)
            raise

    def run_forever(self, stop_event: Any) -> None:
        while not stop_event.is_set():
            cycle_started = time.monotonic()
            try:
                self.run_once()
            except Exception:
                # The loop must survive transient panel/network failures and
                # keep the last successful catalog in place.
                pass
            elapsed = time.monotonic() - cycle_started
            base_wait = max(60.0, self._interval_seconds - elapsed)
            jitter = random.uniform(0, min(60.0, base_wait * 0.05))
            wait_seconds = base_wait + jitter
            LOGGER.info("sync_sleep", extra={"wait_seconds": round(wait_seconds, 1)})
            stop_event.wait(wait_seconds)


def _safe_error(exc: BaseException) -> str:
    message = str(exc).strip() or type(exc).__name__
    message = re.sub(r"(?i)(username|password)=([^&\s]+)", r"\1=***", message)
    message = re.sub(r"https?://\S+", "<url>", message)
    return f"{type(exc).__name__}: {message}"[:500]


def _utc_now() -> str:
    from datetime import datetime, timezone

    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
