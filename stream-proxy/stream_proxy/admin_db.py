"""Admin-managed source URLs in the catalog SQLite database.

The synced ``movies`` table is owned by catalog-sync and is replaced on every
sync. Manual entries therefore live in ``manual_movies`` in the same database;
that table survives a sync and is written with WAL + BEGIN IMMEDIATE.
"""

from __future__ import annotations

import asyncio
import logging
import re
import secrets
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import unquote, urlparse

import aiosqlite

LOGGER = logging.getLogger(__name__)

_SCHEMA = """
CREATE TABLE IF NOT EXISTS manual_movies (
    stream_id INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    source_url TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_manual_movies_title ON manual_movies(title);
"""

_ID_MIN = 2_000_000_000
_ID_SPAN = 100_000_000


class AdminDatabaseError(RuntimeError):
    pass


@dataclass(frozen=True)
class AdminMovie:
    stream_id: int
    title: str
    source_url: str | None
    origin: str  # sync | manual


def _parse_title(source_url: str) -> str:
    parsed = urlparse(source_url)
    path = unquote(parsed.path)
    stem = Path(path).stem if path else ""
    title = re.sub(r"[_\-.]+", " ", stem).strip()
    if not title:
        title = parsed.hostname or "Manueller Stream"
    return title[:120].title()


class AdminDatabase:
    def __init__(self, path: str) -> None:
        self._path = Path(path)
        self._init_lock = asyncio.Lock()
        self._initialized = False

    @property
    def path(self) -> Path:
        return self._path

    async def ensure_schema(self) -> None:
        if self._initialized:
            return
        async with self._init_lock:
            if self._initialized:
                return
            self._path.parent.mkdir(parents=True, exist_ok=True)
            async with aiosqlite.connect(self._path, timeout=10.0) as conn:
                await conn.execute("PRAGMA journal_mode=WAL")
                await conn.execute("PRAGMA busy_timeout=10000")
                await conn.executescript(_SCHEMA)
                await conn.commit()
            self._initialized = True

    async def add_movie_to_db(self, source_url: str) -> AdminMovie:
        normalized = source_url.strip()
        if len(normalized) > 4096 or any(ord(ch) < 32 for ch in normalized):
            raise AdminDatabaseError("Die Quell-URL ist ungültig oder zu lang.")
        parsed = urlparse(normalized)
        if parsed.scheme not in {"http", "https"} or not parsed.netloc:
            raise AdminDatabaseError("Bitte eine gültige HTTP(S)-Quell-URL eingeben.")
        title = _parse_title(normalized)

        await self.ensure_schema()
        async with aiosqlite.connect(self._path, timeout=10.0) as conn:
            await conn.execute("PRAGMA busy_timeout=10000")
            await conn.execute("BEGIN IMMEDIATE")
            try:
                stream_id = await self._new_stream_id(conn)
                await conn.execute(
                    """
                    INSERT INTO manual_movies (stream_id, title, source_url, created_at)
                    VALUES (?, ?, ?, ?)
                    """,
                    (stream_id, title, normalized, _utc_now()),
                )
                await conn.commit()
            except sqlite3.IntegrityError:
                await conn.rollback()
                raise AdminDatabaseError("Diese Quell-URL ist bereits vorhanden.") from None
            except Exception:
                await conn.rollback()
                raise
        LOGGER.info("manual_movie_added", extra={"stream_id": stream_id})
        return AdminMovie(stream_id, title, normalized, "manual")

    async def delete_movie(self, stream_id: int) -> bool:
        await self.ensure_schema()
        async with aiosqlite.connect(self._path, timeout=10.0) as conn:
            await conn.execute("PRAGMA busy_timeout=10000")
            await conn.execute("BEGIN IMMEDIATE")
            try:
                cursor = await conn.execute(
                    "DELETE FROM manual_movies WHERE stream_id = ?",
                    (stream_id,),
                )
                deleted = cursor.rowcount > 0
                await conn.commit()
            except Exception:
                await conn.rollback()
                raise
        if deleted:
            LOGGER.info("manual_movie_deleted", extra={"stream_id": stream_id})
        return deleted

    async def fetch_manual(self, stream_id: int):
        from .db import StreamRecord

        await self.ensure_schema()
        async with aiosqlite.connect(self._path, timeout=5.0) as conn:
            conn.row_factory = aiosqlite.Row
            async with conn.execute(
                "SELECT stream_id, source_url FROM manual_movies WHERE stream_id = ? LIMIT 1",
                (stream_id,),
            ) as cursor:
                row = await cursor.fetchone()
                return StreamRecord(int(row["stream_id"]), row["source_url"]) if row else None

    async def list_all(self) -> list[AdminMovie]:
        await self.ensure_schema()
        movies: list[AdminMovie] = []
        async with aiosqlite.connect(self._path, timeout=5.0) as conn:
            conn.row_factory = aiosqlite.Row
            try:
                async with conn.execute(
                    "SELECT stream_id, title, source_url FROM movies ORDER BY title COLLATE NOCASE",
                ) as cursor:
                    for row in await cursor.fetchall():
                        movies.append(
                            AdminMovie(
                                int(row["stream_id"]),
                                str(row["title"]),
                                row["source_url"],
                                "sync",
                            )
                        )
            except sqlite3.OperationalError:
                pass
            async with conn.execute(
                "SELECT stream_id, title, source_url FROM manual_movies "
                "ORDER BY title COLLATE NOCASE",
            ) as cursor:
                for row in await cursor.fetchall():
                    movies.append(
                        AdminMovie(
                            int(row["stream_id"]),
                            str(row["title"]),
                            row["source_url"],
                            "manual",
                        )
                    )
        return movies

    async def _new_stream_id(self, conn: aiosqlite.Connection) -> int:
        for _ in range(100):
            candidate = _ID_MIN + secrets.randbelow(_ID_SPAN)
            async with conn.execute(
                "SELECT 1 FROM manual_movies WHERE stream_id = ? LIMIT 1",
                (candidate,),
            ) as cursor:
                if await cursor.fetchone():
                    continue
            try:
                async with conn.execute(
                    "SELECT 1 FROM movies WHERE stream_id = ? LIMIT 1",
                    (candidate,),
                ) as cursor:
                    if await cursor.fetchone():
                        continue
            except sqlite3.OperationalError:
                pass
            return candidate
        raise AdminDatabaseError("Keine freie stream_id gefunden.")


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
