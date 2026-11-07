from __future__ import annotations

import json
from pathlib import Path

import pytest

from catalog_sync.netzkino import NETZKINO_VIDEO_BASE, NetzkinoError, NetzkinoParser
from catalog_sync.source import KisiumCatalogSource

FIXTURES = Path(__file__).parent / "fixtures"
PAGE = json.loads((FIXTURES / "netzkino_page.json").read_text(encoding="utf-8"))


class FakeClient:
    def __init__(self, pages):
        self.pages = pages
        self.calls = 0

    def post_json(self, url, payload):
        self.calls += 1
        return self.pages[min(self.calls - 1, len(self.pages) - 1)]


def make_source(page_size: int = 100, pages=None, max_pages: int = 100) -> KisiumCatalogSource:
    return KisiumCatalogSource(
        client=FakeClient(pages or [PAGE]),
        parser=NetzkinoParser(),
        feed_url="https://data.netzkino.de/netzkino/graphql",
        source_id="netzkino:test",
        page_size=page_size,
        fetch_page_size=100,
        max_pages=max_pages,
    )


def test_parses_netzkino_metadata_and_skips_dash_only() -> None:
    movies = make_source().get_latest_movies()
    assert [m.title for m in movies] == ["100.000 verdammte Dollar", "100° Below Zero"]
    first = movies[0]
    assert first.category_name == "Western"
    assert first.image_url == "https://img.example/cover.jpg"
    assert first.backdrop_url == "https://img.example/wide.jpg"
    assert first.year == 1967
    assert first.rating == 7.1
    assert first.container_extension == "mp4"
    assert first.source_url == NETZKINO_VIDEO_BASE + "Great_Movies_NK/100000_verdammte_Dollar.mp4"
    assert first.is_logo is False


def test_stable_ids_across_parses() -> None:
    assert [m.stream_id for m in make_source().get_latest_movies()] == [
        m.stream_id for m in make_source().get_latest_movies()
    ]


def test_page_size_and_pagination() -> None:
    source = make_source(page_size=1)
    assert [m.title for m in source.get_latest_movies(1)] == ["100.000 verdammte Dollar"]
    assert [m.title for m in source.get_latest_movies(2)] == ["100° Below Zero"]
    assert source.get_latest_movies(3) == []


def test_cache_prevents_duplicate_api_calls() -> None:
    client = FakeClient([PAGE])
    source = KisiumCatalogSource(
        client=client,
        parser=NetzkinoParser(),
        feed_url="https://data.netzkino.de/netzkino/graphql",
        source_id="netzkino:test",
        page_size=1,
    )
    source.get_latest_movies(1)
    source.get_latest_movies(2)
    source.format_for_kisium()
    assert client.calls == 1


def test_format_contract_has_metadata_but_no_source_url() -> None:
    payload = make_source().format_for_kisium()
    assert payload["source"]["type"] == "netzkino"
    assert payload["pagination"]["total"] == 2
    assert payload["movies"][0]["backdrop_url"] == "https://img.example/wide.jpg"
    assert payload["movies"][0]["plot"]
    assert payload["movies"][0]["year"] == 1967
    assert "source_url" not in payload["movies"][0]
    assert "pmd.netzkino-seite" not in str(payload)


def test_graphql_error_is_rejected() -> None:
    with pytest.raises(NetzkinoError):
        NetzkinoParser().parse({"errors": [{"message": "bad"}]})
