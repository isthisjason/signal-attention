import math
from typing import Any


DEFAULT_TORCH_MODEL_CONFIG = {
    "dModel": 32,
    "numHeads": 4,
    "numLayers": 2,
    "dimFeedforward": 64,
    "dropout": 0.1,
    "usePositionalEncoding": True,
}


def sinusoidal_positional_encoding(sequence_length: int, d_model: int) -> list[list[float]]:
    """Build a parameter free sinusoidal position table.

    Keeping this in plain Python (no learned weights) means trained artifacts stay
    portable and an older artifact without the flag still loads with the same shape.
    """
    encoding: list[list[float]] = []
    for position in range(sequence_length):
        row: list[float] = []
        for index in range(d_model):
            divisor = math.pow(10000.0, (2 * (index // 2)) / d_model)
            angle = position / divisor
            row.append(math.sin(angle) if index % 2 == 0 else math.cos(angle))
        encoding.append(row)
    return encoding


def build_transformer_model(
    torch: Any,
    *,
    feature_count: int,
    class_count: int,
    config: dict[str, Any] | None = None,
):
    model_config = {**DEFAULT_TORCH_MODEL_CONFIG, **(config or {})}
    d_model = int(model_config["dModel"])
    use_positional_encoding = bool(model_config.get("usePositionalEncoding", True))

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

        def positional_encoding(self, sequence_length: int, device, dtype):
            table = sinusoidal_positional_encoding(sequence_length, d_model)
            return torch.tensor(table, device=device, dtype=dtype)

        def forward(self, inputs):
            projected = self.input_projection(inputs)
            if use_positional_encoding:
                positions = self.positional_encoding(
                    projected.shape[1],
                    projected.device,
                    projected.dtype,
                )
                projected = projected + positions
            encoded = self.encoder(projected)
            pooled = encoded.mean(dim=1)
            return self.classifier(pooled)

    return MarketRegimeTransformer()
