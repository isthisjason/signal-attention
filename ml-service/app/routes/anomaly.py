from fastapi import APIRouter

from app.schemas.anomaly_schema import AnomalyRequest, AnomalyResponse
from app.services.anomaly_service import detect_anomaly

router = APIRouter(prefix="/predict", tags=["anomaly"])


@router.post("/anomaly", response_model=AnomalyResponse)
def predict_anomaly(request: AnomalyRequest) -> AnomalyResponse:
    return detect_anomaly(request)
