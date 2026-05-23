from typing import Any


DEFAULT_TORCH_MODEL_CONFIG = {
    "dModel": 32,
    "numHeads": 4,
    "numLayers": 2,
    "dimFeedforward": 64,
    "dropout": 0.0,
}


def build_transformer_model(
    torch: Any,
    *,
    feature_count: int,
    class_count: int,
    config: dict[str, Any] | None = None,
):
    model_config = {**DEFAULT_TORCH_MODEL_CONFIG, **(config or {})}
    d_model = int(model_config["dModel"])

    class MarketRegimeTransformer(torch.nn.Module):
        def __init__(self) -> None:
            super().__init__()
            self.input_projection = torch.nn.Linear(feature_count, d_model)
            encoder_layer = torch.nn.TransformerEncoderLayer(
                d_model=d_model,
                nhead=int(model_config["numHeads"]),
                dim_feedforward=int(model_config["dimFeedforward"]),
                dropout=float(model_config["dropout"]),
                batch_first=True,
            )
            self.encoder = torch.nn.TransformerEncoder(
                encoder_layer,
                num_layers=int(model_config["numLayers"]),
            )
            self.classifier = torch.nn.Linear(d_model, class_count)

        def forward(self, inputs):
            projected = self.input_projection(inputs)
            encoded = self.encoder(projected)
            pooled = encoded.mean(dim=1)
            return self.classifier(pooled)

    return MarketRegimeTransformer()
