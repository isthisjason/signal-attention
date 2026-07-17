from datetime import UTC, datetime, timedelta
from decimal import Decimal

from app.schemas.market_regime_schema import MarketRegimeCandle
from app.services.market_regime_dataset import build_dataset_readiness, candle_gaps, three_way_split


def candles_for_closes(closes: list[Decimal]) -> list[MarketRegimeCandle]:
    return [
        MarketRegimeCandle(
            openTime=datetime(2024, 1, 1, tzinfo=UTC) + timedelta(hours=index),
            open=close,
            high=close + Decimal("1"),
            low=close - Decimal("1"),
            close=close,
            volume=Decimal("100"),
        )
        for index, close in enumerate(closes)
    ]


def test_three_way_split_is_chronological() -> None:
    train, validation, test = three_way_split(list(range(10)), 0.2, 0.2)

    assert train == [0, 1, 2, 3, 4, 5]
    assert validation == [6, 7]
    assert test == [8, 9]


def test_small_single_label_dataset_fails_readiness() -> None:
    candles = candles_for_closes([Decimal("100") for _ in range(48)])

    report = build_dataset_readiness(candles, 20)

    assert report["ready"] is False
    assert "window count is below 1000" in report["failures"]
    assert report["splits"]["train"]["SIDEWAYS"] > 0


def test_diverse_fixture_passes_configurable_readiness_thresholds(monkeypatch) -> None:
    candles = candles_for_closes([Decimal(index + 100) for index in range(30)])
    # Readiness rules operate on label coverage, so isolate them from classifier threshold details.
    monkeypatch.setattr(
        "app.services.market_regime_dataset.build_labeled_windows",
        lambda _candles, _length: ([[] for _ in range(12)], [0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1]),
    )

    report = build_dataset_readiness(
        candles,
        20,
        min_windows=10,
        min_evaluation_support=1,
    )

    assert report["ready"] is True
    assert report["splits"]["test"] == {
        "SIDEWAYS": 1,
        "TRENDING_UP": 1,
        "TRENDING_DOWN": 0,
        "HIGH_VOLATILITY": 0,
    }


def test_candle_gaps_reports_missing_intervals() -> None:
    candles = candles_for_closes([Decimal("100"), Decimal("101")])
    candles[1].openTime = candles[0].openTime + timedelta(hours=3)

    assert candle_gaps(candles, 3600)[0]["missingCount"] == 2
