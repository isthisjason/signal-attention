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

TORCH_MODEL_ARCHITECTURE_V1 = "transformer-v1"
TORCH_MODEL_ARCHITECTURE_V2 = "attention-transformer-v2"


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
                # Positional encoding gives the encoder order information without adding trained parameters.
                positions = self.positional_encoding(
                    projected.shape[1],
                    projected.device,
                    projected.dtype,
                )
                projected = projected + positions
            encoded = self.encoder(projected)
            # Mean pooling keeps inference independent of a special classification token.
            pooled = encoded.mean(dim=1)
            return self.classifier(pooled)

    return MarketRegimeTransformer()


def build_attention_transformer_model(
    torch: Any,
    *,
    feature_count: int,
    class_count: int,
    config: dict[str, Any] | None = None,
):
    """Build the v2 architecture that can expose attention weights for diagnostics."""
    model_config = {**DEFAULT_TORCH_MODEL_CONFIG, **(config or {})}
    d_model = int(model_config["dModel"])
    layer_count = int(model_config["numLayers"])
    use_positional_encoding = bool(model_config.get("usePositionalEncoding", True))

    class InspectableAttentionLayer(torch.nn.Module):
        def __init__(self) -> None:
            super().__init__()
            self.attention = torch.nn.MultiheadAttention(
                embed_dim=d_model,
                num_heads=int(model_config["numHeads"]),
                dropout=float(model_config["dropout"]),
                batch_first=True,
            )
            self.attention_norm = torch.nn.LayerNorm(d_model)
            self.feedforward = torch.nn.Sequential(
                torch.nn.Linear(d_model, int(model_config["dimFeedforward"])),
                torch.nn.GELU(),
                torch.nn.Dropout(float(model_config["dropout"])),
                torch.nn.Linear(int(model_config["dimFeedforward"]), d_model),
            )
            self.feedforward_norm = torch.nn.LayerNorm(d_model)
            self.dropout = torch.nn.Dropout(float(model_config["dropout"]))

        def forward(self, inputs, *, return_attention: bool = False):
            attended, weights = self.attention(
                inputs,
                inputs,
                inputs,
                need_weights=return_attention,
                average_attn_weights=False,
            )
            hidden = self.attention_norm(inputs + self.dropout(attended))
            fed = self.feedforward(hidden)
            output = self.feedforward_norm(hidden + self.dropout(fed))
            return output, weights

    class InspectableMarketRegimeTransformer(torch.nn.Module):
        def __init__(self) -> None:
            super().__init__()
            self.input_projection = torch.nn.Linear(feature_count, d_model)
            self.layers = torch.nn.ModuleList([InspectableAttentionLayer() for _ in range(layer_count)])
            self.classifier = torch.nn.Linear(d_model, class_count)

        def positional_encoding(self, sequence_length: int, device, dtype):
            table = sinusoidal_positional_encoding(sequence_length, d_model)
            return torch.tensor(table, device=device, dtype=dtype)

        def forward(self, inputs, *, return_attention: bool = False):
            hidden = self.input_projection(inputs)
            if use_positional_encoding:
                hidden = hidden + self.positional_encoding(hidden.shape[1], hidden.device, hidden.dtype)
            attention_weights = []
            for layer in self.layers:
                hidden, weights = layer(hidden, return_attention=return_attention)
                if return_attention and weights is not None:
                    attention_weights.append(weights)
            pooled = hidden.mean(dim=1)
            logits = self.classifier(pooled)
            if return_attention:
                return logits, attention_weights
            return logits

    return InspectableMarketRegimeTransformer()
