"""FastAPI edge redirect service for direct M3U stream URLs."""

from __future__ import annotations

import hmac
import html
import logging
import re
import secrets
from pathlib import Path
from urllib.parse import urlencode, urlparse

from fastapi import Depends, FastAPI, Form, HTTPException, Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse, Response
from fastapi.security import HTTPBasic, HTTPBasicCredentials

from .admin_db import AdminDatabase, AdminDatabaseError
from .catalog_cache import CatalogJsonCache
from .config import Settings
from .db import CatalogDatabase, DatabaseUnavailable

LOGGER = logging.getLogger(__name__)

_FILENAME = re.compile(r"^(?P<id>\d{1,12})\.(?P<ext>[A-Za-z0-9]{1,10})$")
_CSRF_COOKIE = "kisium_admin_csrf"
_SECURITY_HEADERS = {
    "Cache-Control": "no-store",
    "X-Frame-Options": "DENY",
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
    "Content-Security-Policy": (
        "default-src 'self'; style-src 'self' 'unsafe-inline'; "
        "img-src 'self' data:; form-action 'self'; frame-ancestors 'none'"
    ),
}


def create_app(
    settings: Settings,
    database: CatalogDatabase | None = None,
    admin_database: AdminDatabase | None = None,
) -> FastAPI:
    db = database or CatalogDatabase(settings.db_path)
    admin_db = admin_database
    if admin_db is None and settings.admin_enabled:
        admin_db = AdminDatabase(settings.db_path)

    app = FastAPI(
        title="Kisium Stream Proxy",
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
    )
    app.state.settings = settings
    app.state.db = db
    app.state.admin_db = admin_db
    catalog_cache = CatalogJsonCache(settings.catalog_cache_seconds)
    app.state.catalog_cache = catalog_cache
    security = HTTPBasic(auto_error=False)

    def require_admin(
        credentials: HTTPBasicCredentials | None = Depends(security),
    ) -> None:
        if not settings.admin_enabled:
            raise HTTPException(status_code=503, detail="Admin interface is disabled")
        if credentials is None:
            raise HTTPException(
                status_code=401,
                detail="Admin authentication required",
                headers={"WWW-Authenticate": 'Basic realm="Kisium Admin"'},
            )
        user_ok = hmac.compare_digest(credentials.username, settings.admin_username)
        pass_ok = hmac.compare_digest(credentials.password, settings.admin_password)
        if not (user_ok and pass_ok):
            raise HTTPException(
                status_code=401,
                detail="Admin authentication failed",
                headers={"WWW-Authenticate": 'Basic realm="Kisium Admin"'},
            )

    def csrf_cookie(request: Request) -> str:
        return request.cookies.get(_CSRF_COOKIE) or secrets.token_urlsafe(24)

    def verify_csrf(request: Request, form_token: str) -> None:
        cookie = request.cookies.get(_CSRF_COOKIE)
        if not cookie or not hmac.compare_digest(cookie, form_token):
            raise HTTPException(status_code=403, detail="CSRF validation failed")

    def admin_redirect(message: str | None = None, error: str | None = None) -> RedirectResponse:
        query: dict[str, str] = {}
        if message:
            query["ok"] = message
        if error:
            query["err"] = error
        suffix = f"?{urlencode(query)}" if query else ""
        return RedirectResponse(url=f"/admin{suffix}", status_code=303)

    def admin_response(content: str, token: str) -> Response:
        response = HTMLResponse(content=content)
        response.headers.update(_SECURITY_HEADERS)
        response.set_cookie(
            _CSRF_COOKIE,
            token,
            httponly=True,
            samesite="strict",
            secure=settings.admin_cookie_secure,
            max_age=3600,
        )
        return response

    @app.get("/catalog.json", include_in_schema=False)
    async def catalog_json(request: Request) -> Response:
        path = Path(settings.catalog_json_path)
        if not path.is_file():
            raise HTTPException(status_code=404, detail="Catalog not ready")
        try:
            payload = await catalog_cache.get(path)
        except FileNotFoundError:
            raise HTTPException(status_code=404, detail="Catalog not ready") from None
        except OSError:
            LOGGER.error("catalog_read_failed")
            raise HTTPException(status_code=503, detail="Catalog unavailable") from None

        max_age = catalog_cache.max_age_seconds
        cache_control = (
            f"public, max-age={max_age}"
            if max_age > 0
            else "no-cache, max-age=0, must-revalidate"
        )
        headers = {
            "Cache-Control": cache_control,
            "ETag": payload.etag,
            "X-Content-Type-Options": "nosniff",
        }
        if request.headers.get("if-none-match") == payload.etag:
            return Response(status_code=304, headers=headers)
        return Response(
            content=payload.body,
            media_type="application/json",
            headers=headers,
        )

    @app.get("/healthz")
    async def healthz() -> JSONResponse:
        healthy = await db.healthy()
        return JSONResponse(
            {"status": "ok" if healthy else "unavailable", "db": "ok" if healthy else "missing"},
            status_code=200 if healthy else 503,
        )

    @app.get("/movie/{username}/{password}/{stream_filename}")
    async def movie_redirect(
        username: str,
        password: str,
        stream_filename: str,
    ) -> RedirectResponse:
        if settings.auth_mode == "env":
            user_ok = hmac.compare_digest(username, settings.proxy_username)
            pass_ok = hmac.compare_digest(password, settings.proxy_password)
            if not (user_ok and pass_ok):
                raise HTTPException(status_code=401, detail="Unauthorized")

        if len(stream_filename) > settings.max_filename_length:
            raise HTTPException(status_code=404, detail="Stream not found")
        match = _FILENAME.match(stream_filename)
        if not match:
            raise HTTPException(status_code=404, detail="Stream not found")
        stream_id = int(match.group("id"))
        extension = match.group("ext")

        try:
            record = await db.fetch(stream_id)
            if record is None and admin_db is not None:
                record = await admin_db.fetch_manual(stream_id)
        except (DatabaseUnavailable, AdminDatabaseError):
            raise HTTPException(status_code=503, detail="Catalog unavailable") from None
        if record is None:
            raise HTTPException(status_code=404, detail="Stream not found")

        target_link = record.source_url.strip() if record.source_url else ""
        if not target_link:
            LOGGER.warning("stream_without_source_url")
            raise HTTPException(status_code=404, detail="Stream has no source URL")

        parsed = urlparse(target_link)
        if (
            parsed.scheme not in {"http", "https"}
            or not parsed.netloc
            or len(target_link) > 4096
            or any(ord(ch) < 32 for ch in target_link)
        ):
            LOGGER.error("invalid_target_url")
            raise HTTPException(status_code=502, detail="Invalid target")

        LOGGER.info(
            "stream_redirect",
            extra={"stream_id": stream_id, "extension": extension},
        )
        return RedirectResponse(
            url=target_link,
            status_code=302,
            headers={"Cache-Control": "no-store"},
        )

    @app.get("/admin", response_class=HTMLResponse)
    async def admin_page(
        request: Request,
        _: None = Depends(require_admin),
    ) -> Response:
        if admin_db is None:
            raise HTTPException(status_code=503, detail="Admin interface is disabled")
        token = csrf_cookie(request)
        try:
            movies = await admin_db.list_all()
        except (AdminDatabaseError, OSError):
            LOGGER.error("admin_list_failed")
            raise HTTPException(status_code=503, detail="Catalog unavailable") from None

        rows = []
        for movie in movies:
            source = html.escape(movie.source_url or "—")
            delete_cell = (
                f"""
                <form method="post" action="/admin/delete/{movie.stream_id}" class="inline">
                  <input type="hidden" name="csrf_token" value="{html.escape(token)}">
                  <button class="danger" type="submit">Löschen</button>
                </form>
                """
                if movie.origin == "manual"
                else '<span class="badge">Sync</span>'
            )
            rows.append(
                f"""
                <tr>
                  <td>{html.escape(movie.title)}</td>
                  <td><code>{movie.stream_id}</code></td>
                  <td class="url">{source}</td>
                  <td>{'Manuell' if movie.origin == 'manual' else 'Katalog-Sync'}</td>
                  <td>{delete_cell}</td>
                </tr>
                """
            )
        message = request.query_params.get("ok")
        error = request.query_params.get("err")
        banner = ""
        if message:
            banner = f'<p class="ok">{html.escape(message)}</p>'
        if error:
            banner = f'<p class="err">{html.escape(error)}</p>'

        content = _ADMIN_PAGE.replace("__CSRF__", html.escape(token))
        content = content.replace("__BANNER__", banner)
        content = content.replace("__COUNT__", str(len(movies)))
        content = content.replace(
            "__ROWS__",
            "".join(rows) or '<tr><td colspan="5">Noch keine Filme.</td></tr>',
        )
        return admin_response(content, token)

    @app.post("/admin/add")
    async def admin_add(
        request: Request,
        source_url: str = Form(...),
        csrf_token: str = Form(...),
        _: None = Depends(require_admin),
    ) -> RedirectResponse:
        verify_csrf(request, csrf_token)
        if admin_db is None:
            raise HTTPException(status_code=503, detail="Admin interface is disabled")
        try:
            movie = await admin_db.add_movie_to_db(source_url)
        except AdminDatabaseError as exc:
            return admin_redirect(error=str(exc))
        return admin_redirect(message=f"{movie.title} hinzugefügt (stream_id {movie.stream_id}).")

    @app.post("/admin/delete/{stream_id}")
    async def admin_delete(
        request: Request,
        stream_id: int,
        csrf_token: str = Form(...),
        _: None = Depends(require_admin),
    ) -> RedirectResponse:
        verify_csrf(request, csrf_token)
        if admin_db is None:
            raise HTTPException(status_code=503, detail="Admin interface is disabled")
        deleted = await admin_db.delete_movie(stream_id)
        return admin_redirect(
            message="Eintrag gelöscht." if deleted else None,
            error=None if deleted else "Eintrag nicht gefunden oder Sync-Eintrag.",
        )

    return app


_ADMIN_PAGE = """<!doctype html>
<html lang="de">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Kisium Stream-Verwaltung</title>
  <style>
    :root { color-scheme: dark; --bg:#080b11; --panel:#121923; --line:#263241; --text:#eef2f8; --muted:#94a3b8; --accent:#ff5a36; --ok:#24e0c1; }
    * { box-sizing: border-box; }
    body { margin:0; background:linear-gradient(135deg,#080b11,#131a24); color:var(--text); font:15px/1.5 system-ui,sans-serif; }
    main { max-width:1180px; margin:0 auto; padding:36px 22px 70px; }
    h1 { margin:0 0 6px; font-size:34px; letter-spacing:-1px; }
    h1 span { color:var(--accent); }
    p { color:var(--muted); }
    .card { background:var(--panel); border:1px solid var(--line); border-radius:12px; padding:20px; margin:22px 0; }
    form.add { display:flex; gap:10px; flex-wrap:wrap; }
    input[type=url] { flex:1; min-width:280px; padding:12px 14px; border-radius:8px; border:1px solid var(--line); background:#0b1119; color:var(--text); font:inherit; }
    input[type=url]:focus { outline:2px solid var(--accent); border-color:transparent; }
    button { padding:11px 16px; border:0; border-radius:8px; background:var(--accent); color:#fff; font:700 14px system-ui,sans-serif; cursor:pointer; }
    button:hover { filter:brightness(1.1); }
    button.danger { background:#7f1d1d; }
    table { width:100%; border-collapse:collapse; }
    th,td { text-align:left; padding:11px 10px; border-bottom:1px solid var(--line); vertical-align:middle; }
    th { color:var(--muted); font-size:12px; text-transform:uppercase; letter-spacing:.08em; }
    td.url { max-width:420px; overflow-wrap:anywhere; color:var(--muted); font-size:13px; }
    code { color:var(--ok); }
    .badge { display:inline-block; padding:4px 8px; border-radius:6px; background:#1d2a3a; color:var(--muted); font-size:12px; }
    .ok { color:var(--ok); }
    .err { color:#ff9b9b; }
    .inline { display:inline; }
    .hint { font-size:13px; color:var(--muted); }
  </style>
</head>
<body>
<main>
  <h1>Kisium <span>Stream-Verwaltung</span></h1>
  <p>Manuelle Quellen ergänzen. Katalog-Sync-Einträge werden beim nächsten Sync aktualisiert und sind hier schreibgeschützt.</p>
  __BANNER__
  <section class="card">
    <h2>Quell-URL hinzufügen</h2>
    <form class="add" method="post" action="/admin/add">
      <input type="hidden" name="csrf_token" value="__CSRF__">
      <input type="url" name="source_url" required placeholder="https://cdn.example/film.mp4" autocomplete="off">
      <button type="submit">Hinzufügen</button>
    </form>
    <p class="hint">Die stream_id wird automatisch im Bereich ab 2.000.000.000 vergeben; der Titel wird aus dem Dateinamen abgeleitet.</p>
  </section>
  <section class="card">
    <h2>Filme <small>(__COUNT__)</small></h2>
    <table>
      <thead><tr><th>Titel</th><th>stream_id</th><th>Quell-URL</th><th>Typ</th><th></th></tr></thead>
      <tbody>__ROWS__</tbody>
    </table>
  </section>
</main>
</body>
</html>
"""
