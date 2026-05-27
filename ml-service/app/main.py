from fastapi import FastAPI

from app.routes import anomaly, market_regime, strategy_risk

app = FastAPI(
    title="SignalAttention ML Service",
    version="0.0.1",
    description="Rule-based risk scoring service for SignalAttention backtests.",
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


app.include_router(strategy_risk.router)
app.include_router(market_regime.router)
app.include_router(anomaly.router)
