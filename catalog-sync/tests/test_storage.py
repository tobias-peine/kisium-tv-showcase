from __future__ import annotations

import json
import sqlite3

from catalog_sync.source import KisiumMovie
from catalog_sync.storage import CatalogStorage


def movies() -> list[KisiumMovie]:
    return [
        KisiumMovie(
            102, "Alpha", "Movies", "Movies", "http://img/102.jpg", None, None, "m3u8",
            "https://cdn.example/alpha.m3u8",
        ),
        KisiumMovie(
            101, "Zulu", "Movies", "Movies", None, None, None, "mp4",
            "https://cdn.example/zulu.mp4",
        ),
    ]


def payload() -> dict:
    return {
        "schema_version": 1,
        "generated_at": "2026-11-07T10:00:00Z",
        "source": {"id": "netzkino:test1234", "type": "m3u"},
        "movies": [m.to_kisium("netzkino:test1234") for m in movies()],
    }


def test_json_and_sqlite_outputs(tmp_path) -> None:
    storage = CatalogStorage(tmp_path, "both")
    storage.write(
        payload(),
        movies(),
        "netzkino:test1234",
        "2026-11-07T09:59:00Z",
        "2026-11-07T10:00:00Z",
    )
    assert json.loads(storage.json_path.read_text(encoding="utf-8"))["movies"][0]["title"] == "Alpha"
    conn = sqlite3.connect(storage.sqlite_path)
    try:
        count = conn.execute("SELECT COUNT(*) FROM movies").fetchone()[0]
        source_url = conn.execute(
            "SELECT source_url FROM movies WHERE stream_id = 102",
        ).fetchone()[0]
        run = conn.execute("SELECT status, movie_count FROM sync_runs").fetchone()
    finally:
        conn.close()
    assert count == 2
    assert source_url == "https://cdn.example/alpha.m3u8"
    assert run == ("success", 2)
    assert storage.healthcheck(3600)


def test_failed_run_keeps_last_catalog(tmp_path) -> None:
    storage = CatalogStorage(tmp_path, "both")
    storage.write(payload(), movies(), "netzkino:test1234", "s", "f")
    before = storage.json_path.read_text(encoding="utf-8")
    storage.record_failure("netzkino:test1234", "s2", "Temporary failure")
    assert storage.json_path.read_text(encoding="utf-8") == before
    conn = sqlite3.connect(storage.sqlite_path)
    try:
        status = conn.execute("SELECT status FROM sync_runs ORDER BY id DESC LIMIT 1").fetchone()[0]
    finally:
        conn.close()
    assert status == "failed"
