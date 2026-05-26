import scripts.evaluate_market_regime_model as evaluation_script


def test_parse_args_accepts_experiment_registry_options(monkeypatch, tmp_path) -> None:
    csv_path = tmp_path / "candles.csv"
    artifact_path = tmp_path / "market-regime.pt"
    experiments_dir = tmp_path / "experiments"
    monkeypatch.setattr(
        evaluation_script.sys,
        "argv",
        [
            "evaluate_market_regime_model.py",
            "--csv-path",
            str(csv_path),
            "--artifact",
            str(artifact_path),
            "--experiment-name",
            "baseline",
            "--experiments-dir",
            str(experiments_dir),
        ],
    )

    args = evaluation_script.parse_args()

    assert args.experiment_name == "baseline"
    assert args.experiments_dir == experiments_dir
