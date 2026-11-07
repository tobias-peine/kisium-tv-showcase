from __future__ import annotations

import asyncio
import sqlite3

from stream_proxy.db import CatalogDatabase


def _make_db(path, with_source_url: bool = True) -> None:
    conn = sqlite3.connect(path)
    try:
        conn.execute("PRAGMA journal_mode=WAL")
        source_column = ", source_url TEXT" if with_source_url else ""
        conn.execute(
            f"""
            CREATE TABLE movies (
                stream_id INTEGER PRIMARY KEY,
                title TEXT NOT NULL,
                container_extension TEXT NOT NULL
                {source_column}
            )
            """
        )
        if with_source_url:
            conn.execute(
                "INSERT INTO movies (stream_id, title, container_extension, source_url) "
                "VALUES (101, 'Alpha', 'mp4', 'https://cdn.example/alpha.mp4')"
            )
        else:
            conn.execute(
                "INSERT INTO movies (stream_id, title, container_extension) "
                "VALUES (101, 'Alpha', 'mp4')"
            )
        conn.commit()
        conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    finally:
        conn.close()


def test_read_only_lookup(tmp_path) -> None:
    path = tmp_path / "catalog.sqlite"
    _make_db(path)
    record = asyncio.run(CatalogDatabase(str(path)).fetch(101))
    assert record is not None
    assert record.source_url == "https://cdn.example/alpha.mp4"


def test_missing_source_url_column_is_compatible(tmp_path) -> None:
    path = tmp_path / "old.sqlite"
    _make_db(path, with_source_url=False)
    record = asyncio.run(CatalogDatabase(str(path)).fetch(101))
    assert record is not None
    assert record.source_url is None


def test_missing_database_raises(tmp_path) -> None:
    from stream_proxy.db import DatabaseUnavailable

    try:
        asyncio.run(CatalogDatabase(str(tmp_path / "missing.sqlite")).fetch(1))
    except DatabaseUnavailable:
        pass
    else:
        raise AssertionError("DatabaseUnavailable expected")
