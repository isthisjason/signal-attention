from decimal import Decimal

import pytest
from pydantic import ValidationError

from app.routes.strategy_risk import StrategyRiskRequest, predict_strategy_risk


def risk_request(**overrides: object) -> StrategyRiskRequest:
    payload = {
        "totalReturn": Decimal("18"),
        "maxDrawdown": Decimal("8"),
        "winRate": Decimal("58"),
        "profitFactor": Decimal("1.8"),
        "tradeCount": 24,
        "averageTradeReturn": Decimal("0.8"),
        "feeDrag": Decimal("12"),
        "volatility": Decimal("1.5"),
    }
    payload.update(overrides)
    return StrategyRiskRequest(**payload)


def test_valid_prediction_request() -> None:
    response = predict_strategy_risk(risk_request())

    assert response.riskLabel == "LOW_RISK"
    assert response.riskScore >= Decimal("0")
    assert response.reasons


def test_invalid_prediction_request() -> None:
    with pytest.raises(ValidationError):
        StrategyRiskRequest(**{
            "totalReturn": "18",
            "maxDrawdown": "-1",
            "winRate": "120",
            "profitFactor": "1.8",
            "tradeCount": 24,
            "averageTradeReturn": "0.8",
            "feeDrag": "12",
            "volatility": "1.5",
        })


def test_low_risk_label() -> None:
    response = predict_strategy_risk(risk_request())

    assert response.riskLabel == "LOW_RISK"


def test_medium_risk_label() -> None:
    response = predict_strategy_risk(risk_request(maxDrawdown=Decimal("22"), profitFactor=Decimal("1.2"), tradeCount=12))

    assert response.riskLabel == "MEDIUM_RISK"


def test_high_risk_label() -> None:
    response = predict_strategy_risk(
        risk_request(maxDrawdown=Decimal("40"), profitFactor=Decimal("0.8"), volatility=Decimal("9"), winRate=Decimal("30"))
    )

    assert response.riskLabel == "HIGH_RISK"


def test_likely_overfit_label() -> None:
    response = predict_strategy_risk(risk_request(totalReturn=Decimal("140"), tradeCount=4))

    assert response.riskLabel == "LIKELY_OVERFIT"
