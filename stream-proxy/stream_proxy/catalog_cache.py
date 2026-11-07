"""Small, process-local cache for the generated catalog.json.

The catalog-sync writes the file via an atomic rename. A reader therefore sees
either the old or the new complete file, never a partial document. The cache
keeps one immutable payload per worker and refreshes it after a short TTL.
"""

from __future__ import annotations

import asyncio
import hashlib
import time
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class CatalogPayload:
    body: bytes
    etag: str


class CatalogJsonCache:
    def __init__(self, max_age_seconds: int = 60) -> None:
        self._max_age = max(0, max_age_seconds)
        self._lock = asyncio.Lock()
        self._cached_at = 0.0
        self._cached: CatalogPayload | None = None

    @property
    def max_age_seconds(self) -> int:
        return self._max_age

    async def get(self, path: Path) -> CatalogPayload:
        now = time.monotonic()
        if self._cached is not None and now - self._cached_at < self._max_age:
            return self._cached

        async with self._lock:
            now = time.monotonic()
            if self._cached is not None and now - self._cached_at < self._max_age:
                return self._cached

            body = await asyncio.to_thread(_read_complete_file, path)
            payload = CatalogPayload(
                body=body,
                etag=f'"{hashlib.sha256(body).hexdigest()[:32]}"',
            )
            self._cached = payload
            self._cached_at = now
            return payload


def _read_complete_file(path: Path) -> bytes:
    """Read a file and retry once if an atomic replacement happened mid-read."""
    first = path.stat()
    body = path.read_bytes()
    second = path.stat()
    if first.st_size != second.st_size or first.st_mtime_ns != second.st_mtime_ns:
        body = path.read_bytes()
    return body
