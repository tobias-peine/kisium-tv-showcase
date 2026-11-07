"""Read-only SQLite access for the stream redirect proxy."""

from __future__ import annotations

import logging
import sqlite3
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import quote

import aiosqlite

LOGGER = logging.getLogger(__name__)


class DatabaseUnavailable(RuntimeError):
    pass


@dataclass(frozen=True)
class StreamRecord:
    stream_id: int
    source_url: str | None = None


class CatalogDatabase:
    def __init__(self, path: str) -> None:
        self._path = Path(path)

    @property
    def path(self) -> Path:
        return self._path

    def _uri(self) -> str:
        return f"file:{quote(str(self._path))}?mode=ro"

    async def fetch(self, stream_id: int) -> StreamRecord | None:
        if not self._path.is_file():
            raise DatabaseUnavailable("catalog.sqlite ist noch nicht vorhanden.")
        try:
            async with aiosqlite.connect(self._uri(), uri=True, timeout=5.0) as db:
                db.row_factory = aiosqlite.Row
                await db.execute("PRAGMA query_only=ON")
                try:
                    async with db.execute(
                        "SELECT stream_id, source_url FROM movies WHERE stream_id = ? LIMIT 1",
                        (stream_id,),
                    ) as cursor:
                        row = await cursor.fetchone()
                        return StreamRecord(int(row["stream_id"]), row["source_url"]) if row else None
                except sqlite3.OperationalError as exc:
                    if "no such column: source_url" not in str(exc):
                        raise
                    # Compatibility with catalog-sync databases created before
                    # the optional source_url column was introduced.
                    async with db.execute(
                        "SELECT stream_id FROM movies WHERE stream_id = ? LIMIT 1",
                        (stream_id,),
                    ) as cursor:
                        row = await cursor.fetchone()
                        return StreamRecord(int(row["stream_id"]), None) if row else None
        except sqlite3.Error as exc:
            LOGGER.error("sqlite_read_failed", extra={"error": type(exc).__name__})
            raise DatabaseUnavailable("catalog.sqlite ist nicht lesbar.") from None

    async def healthy(self) -> bool:
        if not self._path.is_file():
            return False
        try:
            async with aiosqlite.connect(self._uri(), uri=True, timeout=3.0) as db:
                await db.execute("PRAGMA query_only=ON")
                async with db.execute("SELECT 1 FROM movies LIMIT 1") as cursor:
                    await cursor.fetchone()
            return True
        except sqlite3.Error:
            return False
