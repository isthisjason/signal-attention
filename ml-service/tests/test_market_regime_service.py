from datetime import UTC, datetime, timedelta
from decimal import Decimal

from app.schemas.market_regime_schema import MarketRegimeCandle, MarketRegimeRequest
from app.services.market_regime_config import MarketRegimeSettings
from app.services.market_regime_service import (
    classify_market_regime,
    get_market_regime_classifier,
    market_regime_status,
    run_market_regime,
)
from app.schemas.market_regime_schema import RegimeRunRequest
from app.services.market_regime_torch_adapter import TorchMarketRegimeClassifier


def candle(index: int, close: Decimal, volume: Decimal = Decimal("1000")) -> MarketRegimeCandle:
    return MarketRegimeCandle(
        openTime=datetime(2024, 1, 1, tzinfo=UTC) + timedelta(hours=index),
        open=close,
        high=close + Decimal("1"),
        low=close - Decimal("1"),
        close=close,
        volume=volume,
    )


def request_for(closes: list[Decimal], volumes: list[Decimal] | None = None) -> MarketRegimeRequest:
    candles = [
        candle(index, close, volumes[index] if volumes is not None else Decimal("1000"))
        for index, close in enumerate(closes)
    ]
    return MarketRegimeRequest(symbol="BTC-USD", timeframe="1h", candles=candles)


def test_classifies_trending_up() -> None:
    closes = [Decimal("100") + Decimal(index) for index in range(20)]

    response = classify_market_regime(request_for(closes))

    assert response.regimeLabel == "TRENDING_UP"
    assert response.confidence >= Decimal("60")
    assert response.reasons


def test_rule_classifier_output_is_stable() -> None:
    closes = [Decimal("100") + Decimal(index) for index in range(20)]

    response = classify_market_regime(request_for(closes))

    assert response.regimeLabel == "TRENDING_UP"
    assert response.classifierSource == "rules"
    assert response.confidence == Decimal("92.00")
    assert response.reasons == ["Price is rising and remains above its sequence average."]
    assert response.features.latestReturnPercent == Decimal("0.85")
    assert response.features.averageReturnPercent == Decimal("0.92")
    assert response.features.volatilityPercent == Decimal("0.05")
    assert response.features.trendSlopePercent == Decimal("1.00")
    assert response.features.smaDistancePercent == Decimal("8.68")
    assert response.features.volumeZScore == Decimal("0.00")


def test_classifies_trending_down() -> None:
    closes = [Decimal("120") - Decimal(index) for index in range(20)]

    response = classify_market_regime(request_for(closes))

    assert response.regimeLabel == "TRENDING_DOWN"
    assert response.confidence >= Decimal("60")


def test_rejects_invalid_market_regime_mode() -> None:
    settings = MarketRegimeSettings(mode="unknown")

    try:
        get_market_regime_classifier(settings)
    except ValueError as exc:
        assert str(exc) == "Unsupported market regime mode: unknown"
    else:
        raise AssertionError("Expected invalid market regime mode to fail")


def test_selects_torch_market_regime_mode() -> None:
    settings = MarketRegimeSettings(mode="torch", artifact_path="models/regime.pt")

    classifier = get_market_regime_classifier(settings)

    assert isinstance(classifier, TorchMarketRegimeClassifier)


def test_auto_mode_falls_back_to_rules_when_artifact_is_missing(tmp_path) -> None:
    settings = MarketRegimeSettings(mode="auto", artifact_path=str(tmp_path / "missing.pt"))

    classifier = get_market_regime_classifier(settings)
    status = market_regime_status(settings)

    assert classifier.__class__.__name__ == "RuleBasedMarketRegimeClassifier"
    assert status.mode == "auto"
    assert status.effectiveMode == "rules"
    assert status.ready is True
    assert status.warnings


def test_auto_mode_reports_torch_artifact_when_present(tmp_path) -> None:
    artifact = tmp_path / "market-regime.pt"
    artifact.write_text("placeholder")
    settings = MarketRegimeSettings(mode="auto", artifact_path=str(artifact))

    status = market_regime_status(settings)

    assert status.effectiveMode == "torch"
    assert status.artifactExists is True
    assert status.artifactIdentifier is not None


def test_classifies_sideways() -> None:
    closes = [Decimal("100"), Decimal("101")] * 10

    response = classify_market_regime(request_for(closes))

    assert response.regimeLabel == "SIDEWAYS"


def test_classifies_high_volatility() -> None:
    closes = [Decimal("100"), Decimal("110"), Decimal("96"), Decimal("115")] * 5

    response = classify_market_regime(request_for(closes))

    assert response.regimeLabel == "HIGH_VOLATILITY"
    assert response.confidence >= Decimal("70")


def test_mentions_unusual_volume() -> None:
    closes = [Decimal("100") + Decimal(index) for index in range(20)]
    volumes = [Decimal("1000") for _ in range(19)] + [Decimal("1500")]

    response = classify_market_regime(request_for(closes, volumes))

    assert any("volume" in reason for reason in response.reasons)


def test_run_market_regime_batches_windows_and_stride() -> None:
    closes = [Decimal("100") + Decimal(index) for index in range(30)]
    response = run_market_regime(
        RegimeRunRequest(
            symbol="BTC-USD",
            timeframe="1h",
            candles=request_for(closes).candles,
            windowSize=20,
            stride=5,
            includeAnomalies=False,
        )
    )

    assert response.pointCount == 3
    assert response.points[0].windowStart < response.points[0].windowEnd
    assert response.points[0].anomalyLabel is None
    assert response.points[0].baselineRegimeLabel == "TRENDING_UP"
    assert response.points[0].disagreesWithBaseline is False
