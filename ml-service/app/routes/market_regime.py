from fastapi import APIRouter

from app.schemas.market_regime_schema import (
    MarketRegimeRequest,
    MarketRegimeResponse,
    RegimeRunRequest,
    RegimeRunResponse,
)
from app.services.market_regime_service import classify_market_regime, run_market_regime

router = APIRouter(prefix="/predict", tags=["market-regime"])


@router.post("/market-regime", response_model=MarketRegimeResponse)
def predict_market_regime(request: MarketRegimeRequest) -> MarketRegimeResponse:
    return classify_market_regime(request)


@router.post("/regime-run", response_model=RegimeRunResponse)
def predict_market_regime_run(request: RegimeRunRequest) -> RegimeRunResponse:
    return run_market_regime(request)
