from __future__ import annotations

import pytest

from catalog_sync.config import ConfigError, Settings, validate_vod_url


def test_netzkino_endpoint_is_valid() -> None:
    assert validate_vod_url("https://data.netzkino.de/netzkino/graphql")


def test_invalid_url_is_rejected() -> None:
    with pytest.raises(ConfigError):
        validate_vod_url("vods")


def test_source_id_is_stable() -> None:
    a = Settings(feed_url="https://data.netzkino.de/netzkino/graphql")
    b = Settings(feed_url="https://data.netzkino.de/netzkino/graphql")
    assert a.source_id == b.source_id
    assert a.source_id.startswith("netzkino:")
