from decimal import Decimal

from pydantic import BaseModel, Field


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
