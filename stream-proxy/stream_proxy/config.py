"""Configuration for the Kisium stream redirect proxy."""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path


class ConfigError(ValueError):
    pass


def _env_int(name: str, default: int, minimum: int = 0) -> int:
    raw = _env(name)
    if raw is None:
        return default
    try:
        value = int(raw)
    except ValueError as exc:
        raise ConfigError(f"{name} muss eine Ganzzahl sein.") from exc
    if value < minimum:
        raise ConfigError(f"{name} muss >= {minimum} sein.")
    return value


def _env(name: str, default: str | None = None) -> str | None:
    value = os.getenv(name, default)
    if value is None:
        return None
    value = value.strip()
    return value or None


@dataclass(frozen=True)
class Settings:
    host: str = "0.0.0.0"
    port: int = 5000
    db_path: str = "/data/catalog.sqlite"
    catalog_json_path: str = "/data/catalog.json"
    catalog_cache_seconds: int = 60
    auth_mode: str = "env"  # env | off
    proxy_username: str = ""
    proxy_password: str = field(default="", repr=False)
    admin_username: str = ""
    admin_password: str = field(default="", repr=False)
    admin_cookie_secure: bool = False
    max_filename_length: int = 64
    log_level: str = "INFO"

    @classmethod
    def from_env(cls) -> "Settings":
        auth_mode = (_env("PROXY_AUTH_MODE", "env") or "env").lower()
        if auth_mode not in {"env", "off"}:
            raise ConfigError("PROXY_AUTH_MODE muss env oder off sein.")

        proxy_username = _env("PROXY_USERNAME") or ""
        proxy_password = os.getenv("PROXY_PASSWORD") or ""
        if auth_mode == "env" and (not proxy_username or not proxy_password):
            raise ConfigError(
                "PROXY_AUTH_MODE=env benötigt PROXY_USERNAME/PROXY_PASSWORD.",
            )

        try:
            port = int(_env("PROXY_PORT", "5000") or "5000")
        except ValueError as exc:
            raise ConfigError("PROXY_PORT muss eine Ganzzahl sein.") from exc
        if not 1 <= port <= 65535:
            raise ConfigError("PROXY_PORT liegt außerhalb 1–65535.")

        admin_username = _env("ADMIN_USERNAME") or ""
        admin_password = os.getenv("ADMIN_PASSWORD") or ""
        admin_cookie_secure = (_env("ADMIN_COOKIE_SECURE", "false") or "false").lower() in {
            "1", "true", "yes", "ja"
        }
        if bool(admin_username) != bool(admin_password):
            raise ConfigError("ADMIN_USERNAME und ADMIN_PASSWORD müssen gemeinsam gesetzt sein.")

        return cls(
            host=_env("PROXY_HOST", "0.0.0.0") or "0.0.0.0",
            port=port,
            db_path=_env("SQLITE_DB_PATH", "/data/catalog.sqlite") or "/data/catalog.sqlite",
            catalog_json_path=_env("CATALOG_JSON_PATH", "/data/catalog.json")
            or "/data/catalog.json",
            catalog_cache_seconds=_env_int("CATALOG_CACHE_SECONDS", 60, 0),
            auth_mode=auth_mode,
            proxy_username=proxy_username,
            proxy_password=proxy_password,
            admin_username=admin_username,
            admin_password=admin_password,
            admin_cookie_secure=admin_cookie_secure,
            log_level=(_env("LOG_LEVEL", "INFO") or "INFO").upper(),
        )

    @property
    def admin_enabled(self) -> bool:
        return bool(self.admin_username and self.admin_password)

    @property
    def db_exists(self) -> bool:
        return Path(self.db_path).is_file()

    def safe_summary(self) -> dict[str, object]:
        return {
            "host": self.host,
            "port": self.port,
            "db_path": self.db_path,
            "catalog_json_path": self.catalog_json_path,
            "catalog_cache_seconds": self.catalog_cache_seconds,
            "auth_mode": self.auth_mode,
            "admin_enabled": self.admin_enabled,
            "log_level": self.log_level,
        }
