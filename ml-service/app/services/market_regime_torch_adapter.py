from app.schemas.market_regime_schema import MarketRegimeRequest, MarketRegimeResponse
from app.services.market_regime_config import MarketRegimeSettings


class TorchMarketRegimeClassifier:
    def __init__(self, settings: MarketRegimeSettings) -> None:
        self.settings = settings

    def classify(self, request: MarketRegimeRequest) -> MarketRegimeResponse:
        load_torch()
        raise RuntimeError("Torch market regime inference is not implemented yet.")


def load_torch():
    try:
        import torch
    except ModuleNotFoundError as exc:
        raise RuntimeError(
            "PyTorch is required when MARKET_REGIME_MODE=torch. "
            "Install ml-service/requirements-torch.txt to enable torch inference."
        ) from exc
    return torch
