from fastapi import APIRouter

router = APIRouter(prefix="/predict", tags=["strategy-risk"])


@router.post("/strategy-risk", status_code=501)
def predict_strategy_risk() -> dict[str, str]:
    return {"message": "Strategy risk scoring is not implemented yet."}
