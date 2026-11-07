"""Retrying JSON/GraphQL client for public, free VOD APIs."""

from __future__ import annotations

import json
import logging
import threading
import time
from email.utils import parsedate_to_datetime
from typing import Any, Sequence

import requests
from requests.adapters import HTTPAdapter
from tenacity import (
    Retrying,
    retry_if_exception_type,
    stop_after_attempt,
    wait_exponential_jitter,
)

from .config import Settings

LOGGER = logging.getLogger(__name__)

MAX_RESPONSE_BYTES = 64 * 1024 * 1024


class FeedHttpError(RuntimeError):
    """Base class for sanitized VOD API errors."""


class PermanentFeedError(FeedHttpError):
    pass


class TransientFeedError(FeedHttpError):
    def __init__(self, message: str, retry_after: float | None = None) -> None:
        super().__init__(message)
        self.retry_after = retry_after


class HeaderRotator:
    def __init__(self, user_agents: Sequence[str], enabled: bool = True) -> None:
        self._user_agents = tuple(user_agents) or ("KisiumCatalogSync/3.0",)
        self._enabled = enabled

    def headers_for_attempt(self, attempt: int) -> dict[str, str]:
        index = 0 if not self._enabled else max(attempt - 1, 0) % len(self._user_agents)
        return {
            "User-Agent": self._user_agents[index],
            "Accept": "application/json",
            "Accept-Language": "de-DE,de;q=0.9,en;q=0.5",
            "Content-Type": "application/json",
            "Origin": "https://www.netzkino.de",
            "Referer": "https://www.netzkino.de/",
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Kisium-Sync": "3.0",
        }


class RateLimiter:
    def __init__(self, min_interval_seconds: float) -> None:
        self._interval = max(0.0, min_interval_seconds)
        self._next_at = 0.0
        self._lock = threading.Lock()

    def wait(self) -> None:
        if self._interval <= 0:
            return
        with self._lock:
            now = time.monotonic()
            wait_for = max(0.0, self._next_at - now)
            self._next_at = max(now, self._next_at) + self._interval
        if wait_for > 0:
            time.sleep(wait_for)


def _parse_retry_after(value: str | None) -> float | None:
    if not value:
        return None
    value = value.strip()
    try:
        return max(0.0, float(value))
    except ValueError:
        pass
    try:
        dt = parsedate_to_datetime(value)
        return max(0.0, dt.timestamp() - time.time())
    except (TypeError, ValueError, OverflowError):
        return None


class JsonApiClient:
    def __init__(
        self,
        settings: Settings,
        session: requests.Session | None = None,
    ) -> None:
        self._settings = settings
        self._owns_session = session is None
        self._session = session or requests.Session()
        if self._owns_session:
            adapter = HTTPAdapter(pool_connections=2, pool_maxsize=2, max_retries=0)
            self._session.mount("http://", adapter)
            self._session.mount("https://", adapter)
        self._headers = HeaderRotator(settings.user_agents, settings.header_rotation)
        self._limiter = RateLimiter(settings.min_request_interval_seconds)

    def close(self) -> None:
        if self._owns_session:
            self._session.close()

    def post_json(self, url: str, payload: dict[str, Any]) -> dict[str, Any]:
        retryer = Retrying(
            stop=stop_after_attempt(self._settings.max_retries + 1),
            wait=self._wait_strategy,
            retry=retry_if_exception_type(
                (TransientFeedError, requests.ConnectionError, requests.Timeout)
            ),
            reraise=True,
            before_sleep=self._before_sleep,
        )
        for attempt in retryer:
            with attempt:
                return self._request_once(url, payload, attempt.retry_state.attempt_number)
        raise FeedHttpError("VOD-API konnte nicht geladen werden.")

    def _request_once(self, url: str, payload: dict[str, Any], attempt: int) -> dict[str, Any]:
        self._limiter.wait()
        try:
            response = self._session.post(
                url,
                json=payload,
                headers=self._headers.headers_for_attempt(attempt),
                timeout=(
                    self._settings.connect_timeout_seconds,
                    self._settings.request_timeout_seconds,
                ),
                verify=self._settings.verify_tls,
                stream=True,
            )
        except (requests.ConnectionError, requests.Timeout) as exc:
            raise TransientFeedError(
                f"Netzwerkfehler ({type(exc).__name__})",
            ) from None

        with response:
            status = response.status_code
            LOGGER.info("vod_request", extra={"status": status, "attempt": attempt})
            if status in {401, 403}:
                raise PermanentFeedError("VOD-API-Zugriff verweigert (HTTP 401/403).")
            if status == 429:
                raise TransientFeedError(
                    "Rate Limit erreicht (HTTP 429).",
                    retry_after=_parse_retry_after(response.headers.get("Retry-After")),
                )
            if status in {408, 425, 500, 502, 503, 504}:
                raise TransientFeedError(f"Temporärer VOD-API-Fehler (HTTP {status}).")
            if status >= 400:
                raise PermanentFeedError(f"VOD-API-Fehler (HTTP {status}).")

            chunks = bytearray()
            for chunk in response.iter_content(chunk_size=32 * 1024):
                if not chunk:
                    continue
                chunks.extend(chunk)
                if len(chunks) > MAX_RESPONSE_BYTES:
                    raise PermanentFeedError("VOD-API-Antwort ist zu groß.")
            if not chunks:
                raise PermanentFeedError("VOD-API-Antwort ist leer.")
            try:
                data = json.loads(chunks)
            except ValueError:
                raise PermanentFeedError("VOD-API-Antwort ist kein gültiges JSON.") from None
            if not isinstance(data, dict):
                raise PermanentFeedError("VOD-API-Antwort hat ein unerwartetes Format.")
            return data

    def _wait_strategy(self, retry_state) -> float:
        exc = retry_state.outcome.exception() if retry_state.outcome else None
        if isinstance(exc, TransientFeedError) and exc.retry_after is not None:
            return min(max(exc.retry_after, 1.0), 120.0)
        return float(wait_exponential_jitter(initial=1.0, max=30.0, jitter=2.0)(retry_state))

    def _before_sleep(self, retry_state) -> None:
        exc = retry_state.outcome.exception() if retry_state.outcome else None
        LOGGER.warning(
            "vod_retry",
            extra={
                "attempt": retry_state.attempt_number,
                "error": type(exc).__name__ if exc else "unknown",
                "wait_seconds": retry_state.next_action.sleep if retry_state.next_action else None,
            },
        )
