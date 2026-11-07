from __future__ import annotations

from catalog_sync.config import Settings
from catalog_sync.http import HeaderRotator, JsonApiClient


class FakeResponse:
    def __init__(self, status_code: int, text: str = "", headers=None):
        self.status_code = status_code
        self.headers = headers or {}
        self._chunks = [text.encode("utf-8")] if text else []
        self._entered = False

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def iter_content(self, chunk_size=32768):
        yield from self._chunks


class FakeSession:
    def __init__(self, responses):
        self._responses = iter(responses)
        self.calls = 0

    def post(self, *args, **kwargs):
        self.calls += 1
        return next(self._responses)


def settings() -> Settings:
    return Settings(
        feed_url="https://data.netzkino.de/netzkino/graphql",
        max_retries=2,
        min_request_interval_seconds=0,
    )


def test_header_rotation() -> None:
    rotator = HeaderRotator(("A", "B"), enabled=True)
    assert rotator.headers_for_attempt(1)["User-Agent"] == "A"
    assert rotator.headers_for_attempt(2)["User-Agent"] == "B"
    assert rotator.headers_for_attempt(3)["User-Agent"] == "A"
    assert HeaderRotator(("A", "B"), enabled=False).headers_for_attempt(2)["User-Agent"] == "A"


def test_retry_on_503_then_success() -> None:
    session = FakeSession([FakeResponse(503), FakeResponse(200, '{"ok": true}')])
    client = JsonApiClient(settings(), session=session)
    client._wait_strategy = lambda _state: 0.0  # type: ignore[method-assign]
    assert client.post_json("https://feed.example/graphql", {})["ok"] is True
    assert session.calls == 2


def test_401_is_not_retried() -> None:
    session = FakeSession([FakeResponse(401)])
    client = JsonApiClient(settings(), session=session)
    client._wait_strategy = lambda _state: 0.0  # type: ignore[method-assign]
    try:
        client.post_json("https://feed.example/graphql", {})
    except Exception as exc:
        assert type(exc).__name__ == "PermanentFeedError"
    else:
        raise AssertionError("expected PermanentFeedError")
    assert session.calls == 1
