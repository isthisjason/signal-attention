import pytest
from argparse import Namespace
from datetime import UTC, datetime, timedelta

import scripts.train_market_regime_model as training_script
from app.schemas.market_regime_schema import MarketRegimeCandle
from app.services.market_regime_experiment import (
    build_experiment_manifest,
    describe_path,
    load_experiment_registry,
    upsert_experiment_entry,
    write_experiment_registry,
)
from app.services.market_regime_torch_model import TORCH_MODEL_ARCHITECTURE_V1, TORCH_MODEL_ARCHITECTURE_V2
from scripts.train_market_regime_model import (
    balanced_class_weights,
    build_minibatches,
    build_model_config,
    build_training_model,
    build_training_registry_entry,
    chronological_split_index,
    label_distribution,
    macro_f1_from_label_indexes,
    normalize_window,
    normalization_stats,
    predict_validation_labels,
    reproducibility_block,
    select_best_epoch,
    split_training_windows,
    validation_accuracy,
    validate_split_ratios,
    validate_validation_ratio,
    window_time_ranges,
)


@pytest.mark.parametrize("validation_ratio", [0, -0.1, 1, 1.1])
def test_validate_validation_ratio_rejects_invalid_values(validation_ratio: float) -> None:
    with pytest.raises(SystemExit, match="validation-ratio"):
        validate_validation_ratio(validation_ratio)


def test_validate_validation_ratio_accepts_fractional_values() -> None:
    validate_validation_ratio(0.2)


def test_validate_split_ratios_requires_room_for_training() -> None:
    with pytest.raises(SystemExit, match="sum to less than 1"):
        validate_split_ratios(0.5, 0.5)


def test_parse_args_accepts_experiment_registry_options(monkeypatch, tmp_path) -> None:
    output_path = tmp_path / "model.pt"
    csv_path = tmp_path / "candles.csv"
    experiments_dir = tmp_path / "experiments"
    monkeypatch.setattr(
        training_script.sys,
        "argv",
        [
            "train_market_regime_model.py",
            "--csv-path",
            str(csv_path),
            "--output",
            str(output_path),
            "--experiment-name",
            "baseline",
            "--experiments-dir",
            str(experiments_dir),
        ],
    )

    args = training_script.parse_args()

    assert args.experiment_name == "baseline"
    assert args.experiments_dir == experiments_dir
    assert args.architecture == TORCH_MODEL_ARCHITECTURE_V1


def test_parse_args_accepts_attention_architecture(monkeypatch, tmp_path) -> None:
    output_path = tmp_path / "model.pt"
    csv_path = tmp_path / "candles.csv"
    monkeypatch.setattr(
        training_script.sys,
        "argv",
        [
            "train_market_regime_model.py",
            "--csv-path",
            str(csv_path),
            "--output",
            str(output_path),
            "--architecture",
            TORCH_MODEL_ARCHITECTURE_V2,
        ],
    )

    args = training_script.parse_args()

    assert args.architecture == TORCH_MODEL_ARCHITECTURE_V2


def test_build_training_registry_entry_uses_manifest_metrics(tmp_path) -> None:
    args = Namespace(
        experiment_name="baseline",
        csv_path=tmp_path / "candles.csv",
        output=tmp_path / "market-regime.pt",
        model_version="local-transformer-v1",
        sequence_length=20,
    )
    manifest = {
        "schemaVersion": "market-regime-experiment/v1",
        "generatedAt": "2026-05-27T20:00:00+00:00",
        "dataset": {"path": str(args.csv_path), "name": "candles.csv", "sizeBytes": 15, "sha256": "dataset"},
        "artifact": {"path": str(args.output), "name": "market-regime.pt", "sizeBytes": 30, "sha256": "artifact"},
        "featureVersion": "torch-market-regime-features/v1",
        "labels": ["SIDEWAYS"],
        "windowRanges": {
            "all": {"firstWindowEnd": "2024-01-01T02:00:00+00:00", "lastWindowEnd": "2024-01-01T09:00:00+00:00"},
            "train": {"firstWindowEnd": "2024-01-01T02:00:00+00:00", "lastWindowEnd": "2024-01-01T07:00:00+00:00"},
            "validation": {"firstWindowEnd": "2024-01-01T08:00:00+00:00", "lastWindowEnd": "2024-01-01T09:00:00+00:00"},
        },
        "trainWindowCount": 8,
        "validationWindowCount": 2,
        "validationRatio": 0.2,
        "finalTrainLoss": 0.12,
        "validationAccuracy": 0.75,
        "training": {"architecture": TORCH_MODEL_ARCHITECTURE_V2},
    }

    entry = build_training_registry_entry(args, tmp_path / "market-regime.pt.manifest.json", manifest, "run-123")

    assert entry["name"] == "baseline"
    assert entry["runId"] == "run-123"
    assert entry["dataset"]["sha256"] == "dataset"
    assert entry["artifact"]["sha256"] == "artifact"
    assert entry["architecture"] == TORCH_MODEL_ARCHITECTURE_V2
    assert entry["labels"] == ["SIDEWAYS"]
    assert entry["windowRanges"]["validation"]["firstWindowEnd"] == "2024-01-01T08:00:00+00:00"
    assert entry["training"]["validationAccuracy"] == 0.75


def test_chronological_split_index_uses_validation_ratio() -> None:
    assert chronological_split_index(10, 0.2) == 8


def test_chronological_split_index_keeps_minimum_training_and_validation_windows() -> None:
    assert chronological_split_index(2, 0.2) == 1
    assert chronological_split_index(3, 0.9) == 1


def test_chronological_split_index_rounds_validation_size() -> None:
    assert chronological_split_index(11, 0.25) == 8


def test_chronological_split_index_rejects_too_few_windows() -> None:
    with pytest.raises(ValueError, match="at least two windows"):
        chronological_split_index(1, 0.2)


def test_split_training_windows_preserves_chronological_order() -> None:
    train, validation = split_training_windows([1, 2, 3, 4, 5], 0.4)

    assert train == [1, 2, 3]
    assert validation == [4, 5]


def test_training_normalization_stats_exclude_validation_windows() -> None:
    train_windows = [[[1.0 for _ in range(10)], [3.0 for _ in range(10)]]]
    validation_window = [[100.0 for _ in range(10)]]

    means, stds = normalization_stats(train_windows)

    assert means == [2.0 for _ in range(10)]
    assert stds == [1.0 for _ in range(10)]
    assert normalize_window(validation_window, means, stds) == [[98.0 for _ in range(10)]]


def test_split_training_windows_excludes_validation_items_from_training() -> None:
    train_labels, validation_labels = split_training_windows([0, 1, 2, 3, 4], 0.4)

    assert train_labels == [0, 1, 2]
    assert validation_labels == [3, 4]


def test_predict_validation_labels_returns_label_indexes_and_confidence() -> None:
    predictions = predict_validation_labels(FakeTorch(), FakeModel(), [[[1.0 for _ in range(10)]]], "cpu")

    assert predictions == [{"labelIndex": 1, "confidence": 75.0}]


def test_experiment_manifest_records_split_fields(tmp_path) -> None:
    csv_path = tmp_path / "candles.csv"
    output_path = tmp_path / "market-regime.pt"
    csv_path.write_text("symbol,timeframe\n", encoding="utf-8")
    output_path.write_bytes(b"artifact")

    manifest = build_experiment_manifest(
        csv_path=csv_path,
        output_path=output_path,
        sequence_length=20,
        feature_order=["close"],
        labels=["SIDEWAYS"],
        split={"method": "chronological_holdout"},
        training={"epochs": 1},
        window_count=10,
        train_window_count=8,
        validation_window_count=2,
        validation_ratio=0.2,
        train_label_distribution={"SIDEWAYS": 7, "TRENDING_UP": 1},
        validation_label_distribution={"SIDEWAYS": 1, "TRENDING_UP": 1},
        final_train_loss=0.123456,
        validation_accuracy=0.75,
        window_ranges={"train": {"firstWindowEnd": "2024-01-01T00:00:00+00:00"}},
        device="cpu",
    )

    assert manifest["windowCount"] == 10
    assert manifest["dataset"]["sizeBytes"] == 17
    assert len(manifest["dataset"]["sha256"]) == 64
    assert manifest["artifact"]["sizeBytes"] == 8
    assert len(manifest["artifact"]["sha256"]) == 64
    assert manifest["trainWindowCount"] == 8
    assert manifest["validationWindowCount"] == 2
    assert manifest["validationRatio"] == 0.2
    assert manifest["finalTrainLoss"] == 0.123456
    assert manifest["validationAccuracy"] == 0.75
    assert manifest["windowRanges"] == {"train": {"firstWindowEnd": "2024-01-01T00:00:00+00:00"}}


def test_window_time_ranges_tracks_train_and_validation_windows() -> None:
    candles = [
        MarketRegimeCandle(
            openTime=datetime(2024, 1, 1, tzinfo=UTC) + timedelta(hours=index),
            open="1",
            high="1",
            low="1",
            close="1",
            volume="1",
        )
        for index in range(6)
    ]

    ranges = window_time_ranges(
        candles,
        sequence_length=3,
        train_window_count=2,
        validation_window_count=1,
        test_window_count=1,
    )

    assert ranges == {
        "all": {
            "firstWindowEnd": "2024-01-01T02:00:00+00:00",
            "lastWindowEnd": "2024-01-01T05:00:00+00:00",
        },
        "train": {
            "firstWindowEnd": "2024-01-01T02:00:00+00:00",
            "lastWindowEnd": "2024-01-01T03:00:00+00:00",
        },
        "validation": {
            "firstWindowEnd": "2024-01-01T04:00:00+00:00",
            "lastWindowEnd": "2024-01-01T04:00:00+00:00",
        },
        "test": {
            "firstWindowEnd": "2024-01-01T05:00:00+00:00",
            "lastWindowEnd": "2024-01-01T05:00:00+00:00",
        },
    }


def test_describe_path_handles_missing_files(tmp_path) -> None:
    summary = describe_path(tmp_path / "missing.csv")

    assert summary == {
        "path": str(tmp_path / "missing.csv"),
        "name": "missing.csv",
        "sizeBytes": None,
        "sha256": None,
    }


def test_label_distribution_formats_counts_by_label_name() -> None:
    distribution = label_distribution([0, 1, 1, 3], ["SIDEWAYS", "TRENDING_UP", "TRENDING_DOWN", "HIGH_VOLATILITY"])

    assert distribution == {
        "SIDEWAYS": 1,
        "TRENDING_UP": 2,
        "TRENDING_DOWN": 0,
        "HIGH_VOLATILITY": 1,
    }


def test_validation_accuracy_compares_predictions_to_expected_labels() -> None:
    predictions = [{"labelIndex": 1, "confidence": 75.0}, {"labelIndex": 0, "confidence": 60.0}]

    assert validation_accuracy(predictions, [1, 2]) == 0.5


def test_load_experiment_registry_returns_empty_registry_when_missing(tmp_path) -> None:
    assert load_experiment_registry(tmp_path / "index.json") == {"experiments": []}


def test_load_experiment_registry_reads_existing_json(tmp_path) -> None:
    registry_path = tmp_path / "index.json"
    registry_path.write_text('{"experiments": [{"name": "baseline"}]}', encoding="utf-8")

    assert load_experiment_registry(registry_path) == {"experiments": [{"name": "baseline"}]}


def test_write_experiment_registry_creates_parent_directories(tmp_path) -> None:
    registry_path = tmp_path / "models" / "experiments" / "index.json"

    write_experiment_registry(registry_path, {"experiments": [{"name": "baseline"}]})

    assert registry_path.read_text(encoding="utf-8") == '{\n  "experiments": [\n    {\n      "name": "baseline"\n    }\n  ]\n}\n'


def test_upsert_experiment_entry_inserts_new_entry() -> None:
    registry = upsert_experiment_entry({"experiments": []}, {"name": "baseline", "artifactPath": "model.pt"})

    assert registry["experiments"] == [{"name": "baseline", "artifactPath": "model.pt"}]


def test_upsert_experiment_entry_updates_matching_entry_and_preserves_others() -> None:
    registry = {
        "experiments": [
            {"name": "baseline", "artifactPath": "old.pt", "createdBy": "train"},
            {"name": "other", "artifactPath": "other.pt"},
        ]
    }

    updated = upsert_experiment_entry(registry, {"name": "baseline", "artifactPath": "new.pt"})

    assert updated["experiments"] == [
        {"name": "baseline", "artifactPath": "new.pt", "createdBy": "train"},
        {"name": "other", "artifactPath": "other.pt"},
    ]


def test_build_minibatches_is_deterministic_for_a_seed() -> None:
    first = build_minibatches(10, 4, seed=7)
    second = build_minibatches(10, 4, seed=7)

    assert first == second
    assert sorted(index for batch in first for index in batch) == list(range(10))
    assert [len(batch) for batch in first] == [4, 4, 2]


def test_build_minibatches_changes_order_with_a_different_seed() -> None:
    assert build_minibatches(10, 4, seed=1) != build_minibatches(10, 4, seed=2)


def test_select_best_epoch_prefers_highest_validation_accuracy() -> None:
    history = [
        {"valAccuracy": 0.40},
        {"valAccuracy": 0.70},
        {"valAccuracy": 0.65},
    ]

    assert select_best_epoch(history) == 2


def test_select_best_epoch_breaks_ties_toward_the_earliest_epoch() -> None:
    history = [{"valAccuracy": 0.80}, {"valAccuracy": 0.80}]

    assert select_best_epoch(history) == 1


def test_select_best_epoch_supports_macro_f1() -> None:
    history = [
        {"valAccuracy": 0.9, "valMacroF1": 0.4},
        {"valAccuracy": 0.8, "valMacroF1": 0.7},
    ]

    assert select_best_epoch(history, "valMacroF1") == 2


def test_balanced_class_weights_ignore_absent_classes() -> None:
    assert balanced_class_weights([0, 0, 0, 1], 4) == [0.666667, 2.0, 0.0, 0.0]


def test_macro_f1_averages_classes_with_expected_support() -> None:
    assert macro_f1_from_label_indexes([0, 0, 1, 1], [0, 1, 1, 1], 4) == 0.7333


def test_reproducibility_block_records_seed_and_environment() -> None:
    block = reproducibility_block(123, "2.4.0")

    assert block["seed"] == 123
    assert block["torchVersion"] == "2.4.0"
    assert block["python"]
    assert "gitCommit" in block


def test_build_model_config_overrides_dropout_and_positional_encoding() -> None:
    args = Namespace(dropout=0.3, no_positional_encoding=True)

    config = build_model_config(args)

    assert config["dropout"] == 0.3
    assert config["usePositionalEncoding"] is False


def test_build_model_config_keeps_defaults_when_no_overrides() -> None:
    args = Namespace(dropout=None, no_positional_encoding=False)

    config = build_model_config(args)

    assert config["usePositionalEncoding"] is True
    assert config["dropout"] == 0.1


def test_build_training_model_selects_v1_builder(monkeypatch) -> None:
    calls = []

    def fake_builder(torch, *, feature_count: int, class_count: int, config: dict) -> str:
        calls.append((feature_count, class_count, config))
        return "v1-model"

    monkeypatch.setattr(training_script, "build_transformer_model", fake_builder)

    model = build_training_model("torch", TORCH_MODEL_ARCHITECTURE_V1, {"dropout": 0.1})

    assert model == "v1-model"
    assert calls == [(10, 4, {"dropout": 0.1})]


def test_build_training_model_selects_v2_attention_builder(monkeypatch) -> None:
    calls = []

    def fake_builder(torch, *, feature_count: int, class_count: int, config: dict) -> str:
        calls.append((feature_count, class_count, config))
        return "v2-model"

    monkeypatch.setattr(training_script, "build_attention_transformer_model", fake_builder)

    model = build_training_model("torch", TORCH_MODEL_ARCHITECTURE_V2, {"dropout": 0.1})

    assert model == "v2-model"
    assert calls == [(10, 4, {"dropout": 0.1})]


def test_train_with_early_stopping_stops_on_plateau_and_restores_best_state() -> None:
    from scripts.train_market_regime_model import train_with_early_stopping

    # Validation accuracy schedule per epoch eval: high, high, then collapses.
    schedule = [1, 1, 0, 0, 0]
    torch = EarlyStopTorch()
    model = ScheduledModel(schedule)
    validation_windows = [[[0.0]]]
    validation_labels = [1]

    outcome = train_with_early_stopping(
        torch,
        model,
        FakeOptimizer(),
        FakeLossFunction(),
        EarlyStopTensor(item_count=4),
        EarlyStopTensor(item_count=4),
        validation_windows,
        validation_labels,
        epochs=10,
        batch_size=2,
        patience=2,
        seed=1,
        device="cpu",
    )

    assert outcome["earlyStopped"] is True
    assert outcome["bestEpoch"] == 1
    assert len(outcome["epochHistory"]) == 3
    assert outcome["validationAccuracy"] == 1.0
    assert outcome["validationMacroF1"] == 1.0
    # Best state captured at epoch 1 must be the one reloaded at the end.
    assert model.loaded_state == model.snapshots[0]


class EarlyStopTensor:
    def __init__(self, item_count: int) -> None:
        self.shape = (item_count,)

    def index_select(self, dim, index_tensor):
        return self


class FakeOptimizer:
    def zero_grad(self) -> None:
        return None

    def step(self) -> None:
        return None


class FakeLossValue:
    def backward(self) -> None:
        return None

    def item(self) -> float:
        return 0.5


class FakeLossFunction:
    def __call__(self, output, target) -> FakeLossValue:
        return FakeLossValue()


class ScheduledLogits:
    def __init__(self, label_index: int) -> None:
        self.label_index = label_index

    def __getitem__(self, index):
        return self

    def max(self, dim):
        return FakeScalar(0.9), FakeScalar(self.label_index)


class ScheduledModel:
    def __init__(self, eval_schedule: list[int]) -> None:
        self.eval_schedule = eval_schedule
        self.eval_count = 0
        self.snapshots: list[dict] = []
        self.loaded_state = None

    def train(self) -> None:
        return None

    def eval(self) -> None:
        self.eval_count += 1

    def __call__(self, inputs):
        index = min(self.eval_count, len(self.eval_schedule)) - 1
        index = max(index, 0)
        return ScheduledLogits(self.eval_schedule[index])

    def state_dict(self) -> dict:
        snapshot = {"w": FakeParam(self.eval_count)}
        self.snapshots.append(snapshot)
        return snapshot

    def load_state_dict(self, state) -> None:
        self.loaded_state = state


class FakeParam:
    def __init__(self, value: int) -> None:
        self.value = value

    def detach(self) -> "FakeParam":
        return self

    def clone(self) -> "FakeParam":
        return self


class EarlyStopTorch:
    float32 = "float32"
    long = "long"

    def tensor(self, values, dtype, device):
        return values

    def no_grad(self) -> "FakeNoGrad":
        return FakeNoGrad()

    def softmax(self, logits, dim):
        return logits


class FakeScalar:
    def __init__(self, value: float | int) -> None:
        self.value = value

    def item(self) -> float | int:
        return self.value


class FakeProbabilities:
    def __getitem__(self, index: int) -> "FakeProbabilities":
        return self

    def max(self, dim: int) -> tuple[FakeScalar, FakeScalar]:
        return FakeScalar(0.75), FakeScalar(1)


class FakeNoGrad:
    def __enter__(self) -> None:
        return None

    def __exit__(self, exc_type, exc, traceback) -> None:
        return None


class FakeTorch:
    float32 = "float32"

    def no_grad(self) -> FakeNoGrad:
        return FakeNoGrad()

    def tensor(self, values, dtype, device):
        return values

    def softmax(self, logits, dim: int) -> FakeProbabilities:
        return FakeProbabilities()


class FakeModel:
    def __call__(self, input_tensor):
        return input_tensor
