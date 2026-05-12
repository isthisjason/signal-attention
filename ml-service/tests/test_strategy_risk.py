from decimal import Decimal

from fastapi.testclient import TestClient

from app.main import app
from app.routes.strategy_risk import StrategyRiskRequest, predict_strategy_risk


client = TestClient(app)


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
    response = client.post(
        "/predict/strategy-risk",
        json={
            "totalReturn": "18",
            "maxDrawdown": "8",
            "winRate": "58",
            "profitFactor": "1.8",
            "tradeCount": 24,
            "averageTradeReturn": "0.8",
            "feeDrag": "12",
            "volatility": "1.5",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["riskLabel"] == "LOW_RISK"
    assert Decimal(body["riskScore"]) >= Decimal("0")
    assert body["reasons"]


def test_invalid_prediction_request() -> None:
    response = client.post(
        "/predict/strategy-risk",
        json={
            "totalReturn": "18",
            "maxDrawdown": "-1",
            "winRate": "120",
            "profitFactor": "1.8",
            "tradeCount": 24,
            "averageTradeReturn": "0.8",
            "feeDrag": "12",
            "volatility": "1.5",
        },
    )

    assert response.status_code == 422


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
