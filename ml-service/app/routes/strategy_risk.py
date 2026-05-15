from fastapi import APIRouter

from app.schemas.strategy_risk_schema import StrategyRiskRequest, StrategyRiskResponse
from app.services.strategy_risk_service import score_strategy_risk

router = APIRouter(prefix="/predict", tags=["strategy-risk"])


@router.post("/strategy-risk", response_model=StrategyRiskResponse)
def predict_strategy_risk(request: StrategyRiskRequest) -> StrategyRiskResponse:
    return score_strategy_risk(request)
