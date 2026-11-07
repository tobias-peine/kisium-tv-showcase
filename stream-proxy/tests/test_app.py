from __future__ import annotations

from fastapi.testclient import TestClient

from stream_proxy.app import create_app
from stream_proxy.config import Settings
from stream_proxy.db import DatabaseUnavailable, StreamRecord


class FakeDatabase:
    def __init__(self, records=None, healthy=True, error=None):
        self.records = records or {}
        self._healthy = healthy
        self._error = error

    async def fetch(self, stream_id):
        if self._error:
            raise self._error
        return self.records.get(stream_id)

    async def healthy(self):
        return self._healthy


def make_settings(**overrides) -> Settings:
    values = dict(
        auth_mode="off",
        proxy_username="u",
        proxy_password="p",
    )
    values.update(overrides)
    return Settings(**values)


def client(settings=None, database=None) -> TestClient:
    return TestClient(create_app(settings or make_settings(), database=database))


def test_redirect_uses_source_url_from_database() -> None:
    app = client(
        database=FakeDatabase({101: StreamRecord(101, "https://cdn.example/movie.mp4")}),
    )
    response = app.get("/movie/u/p/101.mp4", follow_redirects=False)
    assert response.status_code == 302
    assert response.headers["location"] == "https://cdn.example/movie.mp4"
    assert response.headers["cache-control"] == "no-store"


def test_missing_source_url_returns_404() -> None:
    app = client(database=FakeDatabase({101: StreamRecord(101, None)}))
    assert app.get("/movie/u/p/101.mp4", follow_redirects=False).status_code == 404


def test_unknown_stream_id_returns_404() -> None:
    app = client(database=FakeDatabase({}))
    assert app.get("/movie/u/p/999.mp4", follow_redirects=False).status_code == 404


def test_malformed_filename_returns_404() -> None:
    app = client(database=FakeDatabase({}))
    assert app.get("/movie/u/p/101", follow_redirects=False).status_code == 404
    assert app.get("/movie/u/p/abc.mp4", follow_redirects=False).status_code == 404


def test_env_auth_rejects_wrong_credentials() -> None:
    app = client(
        settings=make_settings(auth_mode="env", proxy_username="u", proxy_password="p"),
        database=FakeDatabase({101: StreamRecord(101, "https://cdn.example/movie.mp4")}),
    )
    assert app.get("/movie/wrong/p/101.mp4", follow_redirects=False).status_code == 401
    assert app.get("/movie/u/wrong/101.mp4", follow_redirects=False).status_code == 401
    assert app.get("/movie/u/p/101.mp4", follow_redirects=False).status_code == 302


def test_database_unavailable_returns_503() -> None:
    app = client(database=FakeDatabase(error=DatabaseUnavailable("no db")))
    assert app.get("/movie/u/p/101.mp4", follow_redirects=False).status_code == 503


def test_healthcheck() -> None:
    healthy = client(database=FakeDatabase(healthy=True))
    assert healthy.get("/healthz").status_code == 200
    unhealthy = client(database=FakeDatabase(healthy=False))
    assert unhealthy.get("/healthz").status_code == 503


def test_catalog_json_is_served(tmp_path) -> None:
    catalog = tmp_path / "catalog.json"
    catalog.write_text('{"schema_version":1,"movies":[]}', encoding="utf-8")
    app = client(
        settings=make_settings(catalog_json_path=str(catalog)),
        database=FakeDatabase({}),
    )
    response = app.get("/catalog.json")
    assert response.status_code == 200
    assert response.json()["schema_version"] == 1
    assert "max-age=60" in response.headers["cache-control"]


def test_missing_catalog_json_returns_404() -> None:
    app = client(database=FakeDatabase({}))
    assert app.get("/catalog.json").status_code == 404


def test_catalog_json_uses_short_cache(tmp_path) -> None:
    catalog = tmp_path / "catalog.json"
    catalog.write_text('{"schema_version":1,"movies":[{"title":"Alt"}]}', encoding="utf-8")
    app = client(
        settings=make_settings(catalog_json_path=str(catalog)),
        database=FakeDatabase({}),
    )
    first = app.get("/catalog.json")
    assert first.status_code == 200
    assert first.json()["movies"][0]["title"] == "Alt"
    assert "max-age=60" in first.headers["cache-control"]
    catalog.write_text('{"schema_version":1,"movies":[{"title":"Neu"}]}', encoding="utf-8")
    second = app.get("/catalog.json")
    assert second.json()["movies"][0]["title"] == "Alt"


def test_catalog_json_cache_zero_refreshes(tmp_path) -> None:
    catalog = tmp_path / "catalog.json"
    catalog.write_text('{"schema_version":1,"movies":[{"title":"Alt"}]}', encoding="utf-8")
    app = client(
        settings=make_settings(catalog_json_path=str(catalog), catalog_cache_seconds=0),
        database=FakeDatabase({}),
    )
    assert app.get("/catalog.json").json()["movies"][0]["title"] == "Alt"
    catalog.write_text('{"schema_version":1,"movies":[{"title":"Neu"}]}', encoding="utf-8")
    assert app.get("/catalog.json").json()["movies"][0]["title"] == "Neu"


def test_catalog_json_etag_returns_304(tmp_path) -> None:
    catalog = tmp_path / "catalog.json"
    catalog.write_text('{"schema_version":1,"movies":[]}', encoding="utf-8")
    app = client(
        settings=make_settings(catalog_json_path=str(catalog)),
        database=FakeDatabase({}),
    )
    first = app.get("/catalog.json")
    etag = first.headers["etag"]
    second = app.get("/catalog.json", headers={"If-None-Match": etag})
    assert second.status_code == 304
