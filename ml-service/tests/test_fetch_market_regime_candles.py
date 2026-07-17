import json
import urllib.error
from datetime import UTC, datetime

import pytest

import scripts.fetch_market_regime_candles as fetcher


START = datetime(2024, 1, 1, tzinfo=UTC)


def raw_candle(timestamp: int, close: str = "101") -> list:
    return [timestamp, "99", "102", "100", close, "12.5"]


def test_fetch_candles_pages_sorts_deduplicates_and_clips_overfetch() -> None:
    calls = []
    first = int(START.timestamp())

    def request_json(url: str) -> list:
        calls.append(url)
        if len(calls) == 1:
            return [raw_candle(first + 3600), raw_candle(first), raw_candle(first - 3600)]
        return [raw_candle(first + 3600, "102"), raw_candle(first + 300 * 3600)]

    candles = fetcher.fetch_candles(
        product_id="BTC-USD",
        start=START,
        end=START.replace(day=20),
        granularity=3600,
        request_delay=0,
        request_json=request_json,
    )

    assert len(calls) == 2
    assert [candle["time"] for candle in candles] == [first, first + 3600, first + 300 * 3600]
    assert candles[1]["close"] == "102"


def test_fetch_candles_rejects_malformed_and_empty_payloads() -> None:
    with pytest.raises(ValueError, match="must contain"):
        fetcher.fetch_candles(
            product_id="BTC-USD",
            start=START,
            end=START.replace(hour=1),
            granularity=3600,
            request_delay=0,
            request_json=lambda _url: [[1, 2]],
        )

    with pytest.raises(ValueError, match="no candles"):
        fetcher.fetch_candles(
            product_id="BTC-USD",
            start=START,
            end=START.replace(hour=1),
            granularity=3600,
            request_delay=0,
            request_json=lambda _url: [],
        )


def test_write_outputs_csv_and_gap_provenance(tmp_path) -> None:
    first = int(START.timestamp())
    candles = [fetcher.normalize_candle(raw_candle(first)), fetcher.normalize_candle(raw_candle(first + 7200))]
    csv_path = tmp_path / "candles.csv"
    provenance_path = tmp_path / "candles.provenance.json"

    fetcher.write_csv(csv_path, "BTC-USD", 3600, candles)
    fetcher.write_provenance(
        provenance_path,
        output=csv_path,
        product_id="BTC-USD",
        start=START,
        end=START.replace(hour=3),
        granularity=3600,
        candles=candles,
    )

    rows = csv_path.read_text(encoding="utf-8").splitlines()
    provenance = json.loads(provenance_path.read_text(encoding="utf-8"))
    assert rows[0] == "symbol,timeframe,openTime,open,high,low,close,volume"
    assert provenance["rowCount"] == 2
    assert provenance["missingIntervals"][0]["missingCount"] == 1
    assert len(provenance["csv"]["sha256"]) == 64


def test_request_json_retries_rate_limits(monkeypatch) -> None:
    attempts = []

    def urlopen(_request, timeout):
        attempts.append(timeout)
        if len(attempts) < 3:
            raise urllib.error.HTTPError("url", 429, "limited", {}, None)
        return FakeResponse(b"[]")

    monkeypatch.setattr(fetcher.urllib.request, "urlopen", urlopen)
    monkeypatch.setattr(fetcher.time, "sleep", lambda _seconds: None)

    assert fetcher.request_json_with_retries("https://example.test") == []
    assert len(attempts) == 3


class FakeResponse:
    def __init__(self, payload: bytes):
        self.payload = payload

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return None

    def read(self) -> bytes:
        return self.payload
