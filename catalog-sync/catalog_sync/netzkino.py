"""Netzkino GraphQL reader and mapper for the free VOD catalog."""

from __future__ import annotations

import hashlib
import logging
from dataclasses import dataclass
from typing import Any

LOGGER = logging.getLogger(__name__)

NETZKINO_VIDEO_BASE = "https://pmd.netzkino-seite.netzkino.de/"
STREAM_ID_LIMIT = 2_000_000_000

MOVIES_QUERY = """
query KisiumMovies($first: Int!, $after: Cursor) {
  allCmsMovies(
    first: $first
    after: $after
    filter: {isStreamable: {equalTo: true}, hasDrm: {equalTo: false}}
    orderBy: [TITLE_ASC]
  ) {
    totalCount
    pageInfo { hasNextPage endCursor }
    nodes {
      id
      title
      longSynopsis
      mediumSynopsis
      shortSynopsis
      productionYear
      rating
      runtimeInSeconds
      coverImage { masterUrl }
      widescreenImage { masterUrl }
      videoSource { hlsUrl pmdUrl dashUrl }
      cmsMovieContentCategoriesByContentMovieId(first: 5) {
        nodes { category { title slug } }
      }
    }
  }
}
"""


class NetzkinoError(ValueError):
    pass


@dataclass(frozen=True)
class NetzkinoPage:
    movies: list["NetzkinoMovie"]
    total_count: int
    has_next_page: bool
    end_cursor: str | None


@dataclass(frozen=True)
class NetzkinoMovie:
    netzkino_id: str
    title: str
    description: str | None
    year: int | None
    rating: float | None
    runtime_seconds: int | None
    image_url: str | None
    backdrop_url: str | None
    category_name: str
    source_url: str
    container_extension: str
    stream_id: int


def _clean(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    value = value.strip()
    return value or None


def _stream_id(netzkino_id: str) -> int:
    digest = hashlib.sha256(netzkino_id.encode("utf-8")).digest()
    value = int.from_bytes(digest[:4], "big") % STREAM_ID_LIMIT
    return value or 1


def _absolute_media_url(value: str | None) -> str | None:
    value = _clean(value)
    if not value:
        return None
    if value.startswith("http://") or value.startswith("https://"):
        return value
    return NETZKINO_VIDEO_BASE + value.lstrip("/")


def _extension(url: str) -> str:
    path = url.split("?", 1)[0].lower()
    for extension in ("mp4", "m3u8", "webm", "mkv"):
        if path.endswith(f".{extension}"):
            return extension
    return "mp4"


class NetzkinoParser:
    def parse(self, payload: dict[str, Any]) -> NetzkinoPage:
        if payload.get("errors"):
            message = payload["errors"][0].get("message", "unbekannter GraphQL-Fehler")
            raise NetzkinoError(f"Netzkino GraphQL: {message}")

        connection = (payload.get("data") or {}).get("allCmsMovies")
        if not isinstance(connection, dict):
            raise NetzkinoError("Netzkino-Antwort enthält allCmsMovies nicht.")
        nodes = connection.get("nodes") or []
        if not isinstance(nodes, list):
            raise NetzkinoError("Netzkino-Antwort enthält ungültige nodes.")

        movies: list[NetzkinoMovie] = []
        used_ids: set[int] = set()
        seen_urls: set[str] = set()
        for node in nodes:
            if not isinstance(node, dict):
                continue
            title = _clean(node.get("title"))
            source = _clean((node.get("videoSource") or {}).get("pmdUrl"))
            if source:
                source_url = _absolute_media_url(source)
            else:
                # The player supports HLS as a fallback; skip DASH-only entries.
                source_url = _absolute_media_url((node.get("videoSource") or {}).get("hlsUrl"))
            if not title or not source_url or source_url in seen_urls:
                continue

            netzkino_id = _clean(node.get("id")) or source_url
            stream_id = _stream_id(netzkino_id)
            while stream_id in used_ids:
                stream_id = (stream_id + 1) % STREAM_ID_LIMIT or 1
            used_ids.add(stream_id)
            seen_urls.add(source_url)

            categories = (
                (node.get("cmsMovieContentCategoriesByContentMovieId") or {}).get("nodes") or []
            )
            category_name = "Netzkino"
            if categories:
                category = (categories[0] or {}).get("category") or {}
                category_name = _clean(category.get("title")) or category_name

            cover = _clean((node.get("coverImage") or {}).get("masterUrl"))
            wide = _clean((node.get("widescreenImage") or {}).get("masterUrl"))
            description = (
                _clean(node.get("longSynopsis"))
                or _clean(node.get("mediumSynopsis"))
                or _clean(node.get("shortSynopsis"))
            )
            year = node.get("productionYear")
            if not isinstance(year, int):
                year = None
            rating = node.get("rating")
            if not isinstance(rating, (int, float)):
                rating = None
            runtime = node.get("runtimeInSeconds")
            if not isinstance(runtime, (int, float)):
                runtime = None

            movies.append(
                NetzkinoMovie(
                    netzkino_id=netzkino_id,
                    title=title,
                    description=description,
                    year=year,
                    rating=float(rating) if rating is not None else None,
                    runtime_seconds=int(runtime) if runtime is not None else None,
                    image_url=cover,
                    backdrop_url=wide,
                    category_name=category_name,
                    source_url=source_url,
                    container_extension=_extension(source_url),
                    stream_id=stream_id,
                )
            )

        page_info = connection.get("pageInfo") or {}
        total = connection.get("totalCount")
        LOGGER.info(
            "netzkino_page_parsed",
            extra={"movies": len(movies), "total": total if isinstance(total, int) else None},
        )
        return NetzkinoPage(
            movies=movies,
            total_count=int(total) if isinstance(total, int) else 0,
            has_next_page=bool(page_info.get("hasNextPage")),
            end_cursor=_clean(page_info.get("endCursor")),
        )
