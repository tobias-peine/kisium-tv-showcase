from __future__ import annotations

import asyncio
import re

from fastapi.testclient import TestClient

from stream_proxy.admin_db import AdminDatabase, AdminDatabaseError
from stream_proxy.app import create_app
from stream_proxy.config import Settings


class FakeCatalog:
    def __init__(self):
        self.records = {}

    async def fetch(self, stream_id):
        return self.records.get(stream_id)

    async def healthy(self):
        return True


def make_settings(**overrides) -> Settings:
    values = dict(
        auth_mode="off",
        admin_username="admin",
        admin_password="secret",
    )
    values.update(overrides)
    return Settings(**values)


def csrf_from(html: str) -> str:
    match = re.search(r'name="csrf_token" value="([^"]+)"', html)
    assert match, "CSRF token missing"
    return match.group(1)


def test_add_list_delete_manual_movie(tmp_path) -> None:
    db_path = tmp_path / "catalog.sqlite"
    admin_db = AdminDatabase(str(db_path))
    app = create_app(make_settings(), database=FakeCatalog(), admin_database=admin_db)
    with TestClient(app) as client:
        response = client.get("/admin", auth=("admin", "secret"))
        assert response.status_code == 200
        token = csrf_from(response.text)
        assert response.headers["x-frame-options"] == "DENY"
        assert "kisium_admin_csrf" in response.cookies

        response = client.post(
            "/admin/add",
            auth=("admin", "secret"),
            data={
                "source_url": "https://cdn.example/Mein_Film_2024.mp4",
                "csrf_token": token,
            },
            follow_redirects=False,
        )
        assert response.status_code == 303

        movies = asyncio.run(admin_db.list_all())
        manual = [m for m in movies if m.origin == "manual"]
        assert len(manual) == 1
        assert manual[0].title == "Mein Film 2024"
        assert manual[0].stream_id >= 2_000_000_000

        response = client.get("/admin", auth=("admin", "secret"))
        assert "Mein Film 2024" in response.text
        assert "https://cdn.example/Mein_Film_2024.mp4" in response.text

        deleted = asyncio.run(admin_db.delete_movie(manual[0].stream_id))
        assert deleted is True
        assert asyncio.run(admin_db.list_all()) == []


def test_admin_requires_auth_and_csrf(tmp_path) -> None:
    admin_db = AdminDatabase(str(tmp_path / "catalog.sqlite"))
    app = create_app(make_settings(), database=FakeCatalog(), admin_database=admin_db)
    with TestClient(app) as client:
        assert client.get("/admin").status_code == 401
        response = client.get("/admin", auth=("admin", "wrong"))
        assert response.status_code == 401

        page = client.get("/admin", auth=("admin", "secret"))
        assert page.status_code == 200
        response = client.post(
            "/admin/add",
            auth=("admin", "secret"),
            data={"source_url": "https://cdn.example/x.mp4", "csrf_token": "wrong"},
            follow_redirects=False,
        )
        assert response.status_code == 403


def test_admin_disabled(tmp_path) -> None:
    settings = make_settings(admin_username="", admin_password="")
    app = create_app(settings, database=FakeCatalog(), admin_database=None)
    with TestClient(app) as client:
        assert client.get("/admin").status_code == 503


def test_duplicate_source_url_is_rejected(tmp_path) -> None:
    admin_db = AdminDatabase(str(tmp_path / "catalog.sqlite"))
    asyncio.run(admin_db.add_movie_to_db("https://cdn.example/same.mp4"))
    try:
        asyncio.run(admin_db.add_movie_to_db("https://cdn.example/same.mp4"))
    except AdminDatabaseError as exc:
        assert "bereits" in str(exc)
    else:
        raise AssertionError("duplicate URL must be rejected")


def test_proxy_serves_manual_movie(tmp_path) -> None:
    admin_db = AdminDatabase(str(tmp_path / "catalog.sqlite"))
    manual = asyncio.run(admin_db.add_movie_to_db("https://cdn.example/manual.mp4"))
    catalog = FakeCatalog()
    app = create_app(
        make_settings(admin_username="", admin_password=""),
        database=catalog,
        admin_database=admin_db,
    )
    with TestClient(app) as client:
        response = client.get(
            f"/movie/u/p/{manual.stream_id}.mp4",
            follow_redirects=False,
        )
        assert response.status_code == 302
        assert response.headers["location"] == "https://cdn.example/manual.mp4"
