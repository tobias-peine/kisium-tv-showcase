"""Atomic JSON and SQLite writers for the synced catalog."""

from __future__ import annotations

import json
import logging
import os
import sqlite3
import tempfile
import time
from pathlib import Path
from typing import Any, Sequence

from .source import KisiumMovie

LOGGER = logging.getLogger(__name__)

_SCHEMA = """
CREATE TABLE IF NOT EXISTS movies (
    stream_id INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    category_id TEXT,
    category_name TEXT,
    image_url TEXT,
    rating REAL,
    added INTEGER,
    container_extension TEXT NOT NULL,
    source_url TEXT,
    payload_json TEXT NOT NULL,
    synced_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_movies_added ON movies(added DESC);
CREATE INDEX IF NOT EXISTS idx_movies_category ON movies(category_name);
CREATE TABLE IF NOT EXISTS sync_state (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS sync_runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    started_at TEXT NOT NULL,
    finished_at TEXT NOT NULL,
    source_id TEXT NOT NULL,
    movie_count INTEGER NOT NULL,
    status TEXT NOT NULL,
    error TEXT
);
"""


class CatalogStorage:
    def __init__(
        self,
        output_dir: str | Path,
        output_format: str = "both",
        json_filename: str = "catalog.json",
        sqlite_filename: str = "catalog.sqlite",
    ) -> None:
        if output_format not in {"json", "sqlite", "both"}:
            raise ValueError("output_format muss json, sqlite oder both sein.")
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.output_format = output_format
        self.json_path = self.output_dir / json_filename
        self.sqlite_path = self.output_dir / sqlite_filename
        self.status_path = self.output_dir / "sync-status.json"

    def write(
        self,
        payload: dict[str, Any],
        movies: Sequence[KisiumMovie],
        source_id: str,
        started_at: str,
        finished_at: str,
    ) -> None:
        """Write both formats. Each format is independently atomic."""
        if self.output_format in {"json", "both"}:
            self._write_json(payload)
        if self.output_format in {"sqlite", "both"}:
            self._write_sqlite(payload, movies, source_id, started_at, finished_at)
        status = {
            "status": "success",
            "schema_version": payload.get("schema_version", 1),
            "source_id": source_id,
            "generated_at": payload.get("generated_at"),
            "movie_count": len(movies),
            "finished_at": finished_at,
        }
        self._atomic_write_json(self.status_path, status)
        LOGGER.info("catalog_written", extra={"movie_count": len(movies)})

    def record_failure(self, source_id: str, started_at: str, error: str) -> None:
        """Persist a failed run without touching the last good catalog."""
        if self.output_format not in {"sqlite", "both"}:
            return
        try:
            now = _utc_now()
            with self._connect() as conn:
                conn.execute(
                    """
                    INSERT INTO sync_runs
                        (started_at, finished_at, source_id, movie_count, status, error)
                    VALUES (?, ?, ?, 0, 'failed', ?)
                    """,
                    (started_at, now, source_id, error[:500]),
                )
                conn.commit()
        except Exception:
            LOGGER.exception("failed_run_not_recorded")

    def healthcheck(self, max_age_seconds: float) -> bool:
        required: list[Path] = []
        if self.output_format in {"json", "both"}:
            required.append(self.json_path)
        if self.output_format in {"sqlite", "both"}:
            required.append(self.sqlite_path)
        required.append(self.status_path)
        if not all(path.is_file() for path in required):
            return False
        newest = max(path.stat().st_mtime for path in required)
        return (time.time() - newest) <= max_age_seconds

    def _write_json(self, payload: dict[str, Any]) -> None:
        self._atomic_write_json(self.json_path, payload)

    @staticmethod
    def _atomic_write_json(path: Path, payload: dict[str, Any]) -> None:
        directory = path.parent
        fd, temp_name = tempfile.mkstemp(
            prefix=f".{path.name}.",
            suffix=".tmp",
            dir=directory,
        )
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as handle:
                json.dump(payload, handle, ensure_ascii=False, indent=2)
                handle.write("\n")
                handle.flush()
                os.fsync(handle.fileno())
            os.chmod(temp_name, 0o644)
            os.replace(temp_name, path)
            dir_fd = os.open(directory, os.O_DIRECTORY)
            try:
                os.fsync(dir_fd)
            finally:
                os.close(dir_fd)
        except Exception:
            try:
                os.unlink(temp_name)
            except FileNotFoundError:
                pass
            raise

    def _write_sqlite(
        self,
        payload: dict[str, Any],
        movies: Sequence[KisiumMovie],
        source_id: str,
        started_at: str,
        finished_at: str,
    ) -> None:
        source_id = payload.get("source", {}).get("id", source_id)
        with self._connect() as conn:
            conn.execute("BEGIN IMMEDIATE")
            try:
                conn.execute("DELETE FROM movies")
                now = _utc_now()
                conn.executemany(
                    """
                    INSERT INTO movies
                        (stream_id, title, category_id, category_name, image_url,
                         rating, added, container_extension, source_url, payload_json, synced_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    [
                        (
                            movie.stream_id,
                            movie.title,
                            movie.category_id,
                            movie.category_name,
                            movie.image_url,
                            movie.rating,
                            movie.added,
                            movie.container_extension,
                            movie.source_url,
                            json.dumps(movie.to_kisium(source_id), ensure_ascii=False),
                            now,
                        )
                        for movie in movies
                    ],
                )
                conn.execute(
                    """
                    INSERT INTO sync_runs
                        (started_at, finished_at, source_id, movie_count, status, error)
                    VALUES (?, ?, ?, ?, 'success', NULL)
                    """,
                    (started_at, finished_at, source_id, len(movies)),
                )
                conn.executemany(
                    "INSERT OR REPLACE INTO sync_state (key, value) VALUES (?, ?)",
                    [
                        ("source_id", source_id),
                        ("generated_at", str(payload.get("generated_at", ""))),
                        ("movie_count", str(len(movies))),
                        ("last_success_at", finished_at),
                    ],
                )
                conn.commit()
                # Keep the main database current so a read-only WAL reader in
                # the stream proxy sees the latest snapshot without needing
                # write access to the -shm/-wal files.
                conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
            except Exception:
                conn.rollback()
                raise

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.sqlite_path, timeout=30.0)
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA synchronous=NORMAL")
        conn.execute("PRAGMA busy_timeout=30000")
        conn.executescript(_SCHEMA)
        columns = {row[1] for row in conn.execute("PRAGMA table_info(movies)").fetchall()}
        if "source_url" not in columns:
            conn.execute("ALTER TABLE movies ADD COLUMN source_url TEXT")
            conn.commit()
        return conn


def _utc_now() -> str:
    from datetime import datetime, timezone

    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
