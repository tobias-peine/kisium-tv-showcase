"""Configuration for the Netzkino VOD catalog sync service."""

from __future__ import annotations

import hashlib
import os
from dataclasses import dataclass
from urllib.parse import urlparse


class ConfigError(ValueError):
    """Raised when required or invalid environment configuration is detected."""


def _env(name: str, default: str | None = None) -> str | None:
    value = os.getenv(name, default)
    if value is None:
        return None
    value = value.strip()
    return value or None


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


def _env_float(name: str, default: float, minimum: float = 0.0) -> float:
    raw = _env(name)
    if raw is None:
        return default
    try:
        value = float(raw)
    except ValueError as exc:
        raise ConfigError(f"{name} muss eine Zahl sein.") from exc
    if value < minimum:
        raise ConfigError(f"{name} muss >= {minimum} sein.")
    return value


def _env_bool(name: str, default: bool) -> bool:
    raw = _env(name)
    if raw is None:
        return default
    lowered = raw.lower()
    if lowered in {"1", "true", "yes", "on", "ja"}:
        return True
    if lowered in {"0", "false", "no", "off", "nein"}:
        return False
    raise ConfigError(f"{name} muss true/false sein.")


def validate_vod_url(raw: str) -> str:
    value = raw.strip()
    if not value:
        raise ConfigError("VOD_FEED_URL fehlt.")
    parsed = urlparse(value)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise ConfigError("VOD_FEED_URL muss eine gültige HTTP(S)-URL sein.")
    return value


@dataclass(frozen=True)
class Settings:
    feed_url: str
    page_size: int = 100
    fetch_page_size: int = 100
    max_pages: int = 100
    sync_interval_seconds: int = 6 * 60 * 60
    request_timeout_seconds: float = 30.0
    connect_timeout_seconds: float = 10.0
    max_retries: int = 5
    min_request_interval_seconds: float = 0.4
    verify_tls: bool = True
    output_dir: str = "/data"
    output_format: str = "both"
    json_filename: str = "catalog.json"
    sqlite_filename: str = "catalog.sqlite"
    log_level: str = "INFO"
    header_rotation: bool = True
    user_agents: tuple[str, ...] = (
        "KisiumCatalogSync/3.0 (+https://github.com/)",
        "VLC/3.0.20 LibVLC/3.0.20",
        "okhttp/4.12.0",
    )

    @property
    def source_id(self) -> str:
        digest = hashlib.sha256(self.feed_url.encode()).hexdigest()
        return f"netzkino:{digest[:12]}"

    @property
    def feed_host(self) -> str:
        return urlparse(self.feed_url).netloc

    @classmethod
    def from_env(cls) -> "Settings":
        feed_url = validate_vod_url(_env("VOD_FEED_URL", "") or "")

        output_format = (_env("OUTPUT_FORMAT", "both") or "both").lower()
        if output_format not in {"json", "sqlite", "both"}:
            raise ConfigError("OUTPUT_FORMAT muss json, sqlite oder both sein.")

        raw_agents = _env("USER_AGENTS")
        agents = tuple(
            agent.strip()
            for agent in (raw_agents or "").split(",")
            if agent.strip()
        )
        if not agents:
            agents = (
                "KisiumCatalogSync/3.0 (+https://github.com/)",
                "VLC/3.0.20 LibVLC/3.0.20",
                "okhttp/4.12.0",
            )

        return cls(
            feed_url=feed_url,
            page_size=_env_int("PAGE_SIZE", 100, 1),
            fetch_page_size=_env_int("VOD_FETCH_PAGE_SIZE", 100, 1),
            max_pages=_env_int("VOD_MAX_PAGES", 100, 1),
            sync_interval_seconds=_env_int("SYNC_INTERVAL_SECONDS", 6 * 60 * 60, 60),
            request_timeout_seconds=_env_float("REQUEST_TIMEOUT_SECONDS", 30.0, 1.0),
            connect_timeout_seconds=_env_float("CONNECT_TIMEOUT_SECONDS", 10.0, 1.0),
            max_retries=_env_int("MAX_RETRIES", 5, 0),
            min_request_interval_seconds=_env_float("MIN_REQUEST_INTERVAL_SECONDS", 0.4, 0.0),
            verify_tls=_env_bool("VERIFY_TLS", True),
            output_dir=_env("OUTPUT_DIR", "/data") or "/data",
            output_format=output_format,
            json_filename=_env("JSON_FILENAME", "catalog.json") or "catalog.json",
            sqlite_filename=_env("SQLITE_FILENAME", "catalog.sqlite") or "catalog.sqlite",
            log_level=(_env("LOG_LEVEL", "INFO") or "INFO").upper(),
            header_rotation=_env_bool("HEADER_ROTATION", True),
            user_agents=agents,
        )

    def safe_summary(self) -> dict[str, object]:
        return {
            "feed_host": self.feed_host,
            "source_id": self.source_id,
            "page_size": self.page_size,
            "fetch_page_size": self.fetch_page_size,
            "max_pages": self.max_pages,
            "sync_interval_seconds": self.sync_interval_seconds,
            "max_retries": self.max_retries,
            "output_dir": self.output_dir,
            "output_format": self.output_format,
            "verify_tls": self.verify_tls,
            "header_rotation": self.header_rotation,
        }
