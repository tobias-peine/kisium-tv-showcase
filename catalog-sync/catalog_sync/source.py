"""Kisium catalog source backed by the free Netzkino VOD GraphQL API."""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Callable, Sequence

from .http import JsonApiClient
from .netzkino import MOVIES_QUERY, NetzkinoMovie, NetzkinoParser

LOGGER = logging.getLogger(__name__)


@dataclass(frozen=True)
class KisiumMovie:
    stream_id: int
    title: str
    category_id: str | None
    category_name: str | None
    image_url: str | None
    rating: float | None
    added: int | None
    container_extension: str
    source_url: str | None = None
    backdrop_url: str | None = None
    plot: str | None = None
    year: int | None = None
    is_logo: bool = False

    def to_kisium(self, source_id: str) -> dict[str, Any]:
        return {
            "id": str(self.stream_id),
            "stream_id": self.stream_id,
            "source_id": source_id,
            "kind": "MOVIE",
            "title": self.title,
            "category_id": self.category_id,
            "category_name": self.category_name,
            "image_url": self.image_url,
            "backdrop_url": self.backdrop_url,
            "plot": self.plot,
            "year": self.year,
            "rating": self.rating,
            "added": self.added,
            "container_extension": self.container_extension,
            "is_logo": self.is_logo,
        }


class KisiumCatalogSource:
    def __init__(
        self,
        client: JsonApiClient,
        parser: NetzkinoParser,
        feed_url: str,
        source_id: str,
        page_size: int = 100,
        fetch_page_size: int = 100,
        max_pages: int = 100,
        cache_ttl_seconds: float = 300.0,
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        if page_size < 1:
            raise ValueError("page_size muss >= 1 sein.")
        if fetch_page_size < 1:
            raise ValueError("fetch_page_size muss >= 1 sein.")
        if max_pages < 1:
            raise ValueError("max_pages muss >= 1 sein.")
        self._client = client
        self._parser = parser
        self._feed_url = feed_url
        self.source_id = source_id
        self.page_size = page_size
        self._fetch_page_size = fetch_page_size
        self._max_pages = max_pages
        self._cache_ttl_seconds = max(0.0, cache_ttl_seconds)
        self._clock = clock
        self._cache: tuple[float, list[KisiumMovie]] | None = None

    def get_latest_movies(self, page: int = 1) -> list[KisiumMovie]:
        if page < 1:
            raise ValueError("page muss >= 1 sein.")
        movies = self.get_all_movies()
        start = (page - 1) * self.page_size
        return movies[start : start + self.page_size]

    def get_all_movies(self, force_refresh: bool = False) -> list[KisiumMovie]:
        now = self._clock()
        if not force_refresh and self._cache is not None:
            cached_at, cached_movies = self._cache
            if now - cached_at <= self._cache_ttl_seconds:
                return cached_movies

        movies: list[KisiumMovie] = []
        seen_ids: set[int] = set()
        after: str | None = None
        fetched_pages = 0
        while fetched_pages < self._max_pages:
            payload = self._client.post_json(
                self._feed_url,
                {
                    "query": MOVIES_QUERY,
                    "variables": {
                        "first": self._fetch_page_size,
                        "after": after,
                    },
                },
            )
            page = self._parser.parse(payload)
            fetched_pages += 1
            for entry in page.movies:
                if entry.stream_id in seen_ids:
                    continue
                seen_ids.add(entry.stream_id)
                movies.append(_to_kisium(entry))
            if not page.has_next_page or not page.end_cursor:
                break
            after = page.end_cursor

        self._cache = (now, movies)
        LOGGER.info(
            "vod_catalog_loaded",
            extra={
                "movie_count": len(movies),
                "pages": fetched_pages,
                "source_total": page.total_count if fetched_pages else None,
            },
        )
        return movies

    def format_for_kisium(
        self,
        movies: Sequence[KisiumMovie] | None = None,
    ) -> dict[str, Any]:
        selected = list(movies) if movies is not None else self.get_all_movies()
        generated_at = (
            datetime.now(timezone.utc)
            .isoformat(timespec="seconds")
            .replace("+00:00", "Z")
        )
        return {
            "schema_version": 1,
            "generated_at": generated_at,
            "source": {
                "id": self.source_id,
                "type": "netzkino",
            },
            "pagination": {
                "page_size": self.page_size,
                "total": len(selected),
            },
            "movies": [movie.to_kisium(self.source_id) for movie in selected],
        }


def _to_kisium(entry: NetzkinoMovie) -> KisiumMovie:
    return KisiumMovie(
        stream_id=entry.stream_id,
        title=entry.title,
        category_id=entry.category_name,
        category_name=entry.category_name,
        image_url=entry.image_url,
        rating=entry.rating,
        added=None,
        container_extension=entry.container_extension,
        source_url=entry.source_url,
        backdrop_url=entry.backdrop_url,
        plot=entry.description,
        year=entry.year,
        is_logo=False,
    )
