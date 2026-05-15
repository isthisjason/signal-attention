from fastapi import APIRouter

from app.schemas.market_regime_schema import MarketRegimeRequest, MarketRegimeResponse
from app.services.market_regime_service import classify_market_regime

router = APIRouter(prefix="/predict", tags=["market-regime"])


@router.post("/market-regime", response_model=MarketRegimeResponse)
def predict_market_regime(request: MarketRegimeRequest) -> MarketRegimeResponse:
    return classify_market_regime(request)
