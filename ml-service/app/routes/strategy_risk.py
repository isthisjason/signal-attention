from decimal import Decimal

from fastapi import APIRouter
from pydantic import BaseModel, Field

router = APIRouter(prefix="/predict", tags=["strategy-risk"])


class StrategyRiskRequest(BaseModel):
    totalReturn: Decimal = Field(description="Backtest total return percentage.")
    maxDrawdown: Decimal = Field(ge=0, description="Maximum drawdown percentage.")
    winRate: Decimal = Field(ge=0, le=100, description="Winning trade percentage.")
    profitFactor: Decimal = Field(ge=0, description="Gross profit divided by gross loss.")
    tradeCount: int = Field(ge=0, description="Closed trade count.")
    averageTradeReturn: Decimal = Field(description="Average closed trade return percentage.")
    feeDrag: Decimal = Field(ge=0, description="Total fees paid.")
    volatility: Decimal = Field(ge=0, description="Equity return volatility percentage.")


class StrategyRiskResponse(BaseModel):
    riskScore: Decimal
    riskLabel: str
    reasons: list[str]


@router.post("/strategy-risk", response_model=StrategyRiskResponse)
def predict_strategy_risk(request: StrategyRiskRequest) -> StrategyRiskResponse:
    score = Decimal("20")
    reasons: list[str] = []

    if request.tradeCount < 5:
        score += Decimal("35")
        reasons.append("Very low trade count increases overfitting risk.")
    elif request.tradeCount < 15:
        score += Decimal("15")
        reasons.append("Limited trade count weakens confidence.")
    else:
        score -= Decimal("5")
        reasons.append("Trade count is broad enough for a baseline read.")

    if request.totalReturn > Decimal("100") and request.tradeCount < 10:
        score += Decimal("30")
        reasons.append("Very high return with few trades may be overfit.")

    if request.maxDrawdown >= Decimal("35"):
        score += Decimal("25")
        reasons.append("High drawdown increases capital risk.")
    elif request.maxDrawdown >= Decimal("20"):
        score += Decimal("15")
        reasons.append("Moderate drawdown needs review.")
    else:
        score -= Decimal("5")
        reasons.append("Drawdown is controlled.")

    if request.profitFactor < Decimal("1"):
        score += Decimal("25")
        reasons.append("Profit factor below 1.0 indicates losing expectancy.")
    elif request.profitFactor < Decimal("1.5"):
        score += Decimal("10")
        reasons.append("Profit factor is positive but thin.")
    else:
        score -= Decimal("5")
        reasons.append("Profit factor is healthy.")

    if request.volatility >= Decimal("8"):
        score += Decimal("15")
        reasons.append("High equity volatility raises fragility.")
    elif request.volatility <= Decimal("2"):
        score -= Decimal("5")
        reasons.append("Equity volatility is low.")

    if request.winRate < Decimal("35"):
        score += Decimal("10")
        reasons.append("Low win rate can be hard to sustain.")

    score = max(Decimal("0"), min(Decimal("100"), score)).quantize(Decimal("0.01"))

    if request.tradeCount < 5 or (request.totalReturn > Decimal("100") and request.tradeCount < 10):
        label = "LIKELY_OVERFIT"
    elif score >= Decimal("70"):
        label = "HIGH_RISK"
    elif score >= Decimal("40"):
        label = "MEDIUM_RISK"
    else:
        label = "LOW_RISK"

    return StrategyRiskResponse(riskScore=score, riskLabel=label, reasons=reasons)
