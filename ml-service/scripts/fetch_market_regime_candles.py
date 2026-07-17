import argparse
import csv
import hashlib
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Callable


COINBASE_CANDLES_URL = "https://api.exchange.coinbase.com/products/{product_id}/candles"
CSV_FIELDS = ["symbol", "timeframe", "openTime", "open", "high", "low", "close", "volume"]
MAX_CANDLES_PER_REQUEST = 300
ONE_HOUR_SECONDS = 3600


def main() -> None:
    args = parse_args()
    candles = fetch_candles(
        product_id=args.product_id,
        start=args.start,
        end=args.end,
        granularity=args.granularity,
        request_delay=args.request_delay,
    )
    write_csv(args.output, args.product_id, args.granularity, candles)
    provenance_path = args.provenance_output or args.output.with_suffix(".provenance.json")
    write_provenance(
        provenance_path,
        output=args.output,
        product_id=args.product_id,
        start=args.start,
        end=args.end,
        granularity=args.granularity,
        candles=candles,
    )
    print(f"wrote {len(candles)} candles to {args.output}")
    print(f"wrote provenance to {provenance_path}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fetch a reproducible Coinbase candle range for market-regime research.")
    parser.add_argument("--product-id", default="BTC-USD")
    parser.add_argument("--start", type=parse_utc_datetime, default=parse_utc_datetime("2022-01-01T00:00:00Z"))
    parser.add_argument("--end", type=parse_utc_datetime, default=parse_utc_datetime("2025-01-01T00:00:00Z"))
    parser.add_argument("--granularity", type=int, default=ONE_HOUR_SECONDS, choices=[60, 300, 900, 3600, 21600, 86400])
    parser.add_argument("--output", type=Path, default=Path("../data/generated/coinbase-btc-usd-1h-2022-2024.csv"))
    parser.add_argument("--provenance-output", type=Path)
    parser.add_argument("--request-delay", type=float, default=0.15)
    args = parser.parse_args()
    if args.end <= args.start:
        raise SystemExit("--end must be later than --start")
    if args.request_delay < 0:
        raise SystemExit("--request-delay cannot be negative")
    return args


def parse_utc_datetime(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise argparse.ArgumentTypeError("timestamps must include a timezone")
    return parsed.astimezone(UTC)


def fetch_candles(
    *,
    product_id: str,
    start: datetime,
    end: datetime,
    granularity: int,
    request_delay: float,
    request_json: Callable[[str], list] | None = None,
    sleep: Callable[[float], None] = time.sleep,
) -> list[dict[str, str | int]]:
    """Fetch, normalize, and deduplicate a fixed half-open candle interval."""
    requester = request_json or request_json_with_retries
    page_span = timedelta(seconds=granularity * MAX_CANDLES_PER_REQUEST)
    cursor = start
    candles_by_time: dict[int, dict[str, str | int]] = {}

    while cursor < end:
        page_end = min(cursor + page_span, end)
        url = build_url(product_id, cursor, page_end, granularity)
        payload = requester(url)
        if not isinstance(payload, list):
            raise ValueError("Coinbase candle response must be a JSON array")
        for raw_candle in payload:
            candle = normalize_candle(raw_candle)
            timestamp = int(candle["time"])
            # Coinbase can return points before the requested start, so enforce the requested range locally.
            if int(start.timestamp()) <= timestamp < int(end.timestamp()):
                candles_by_time[timestamp] = candle
        cursor = page_end
        if cursor < end and request_delay:
            sleep(request_delay)

    candles = [candles_by_time[timestamp] for timestamp in sorted(candles_by_time)]
    if not candles:
        raise ValueError("Coinbase returned no candles for the requested range")
    return candles


def build_url(product_id: str, start: datetime, end: datetime, granularity: int) -> str:
    query = urllib.parse.urlencode(
        {
            "start": render_datetime(start),
            "end": render_datetime(end),
            "granularity": granularity,
        }
    )
    product = urllib.parse.quote(product_id, safe="-")
    return f"{COINBASE_CANDLES_URL.format(product_id=product)}?{query}"


def request_json_with_retries(url: str, attempts: int = 4) -> list:
    request = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": "SignalAttention/1.0"})
    for attempt in range(attempts):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            if exc.code != 429 and exc.code < 500:
                raise
            if attempt == attempts - 1:
                raise
        except (urllib.error.URLError, TimeoutError):
            if attempt == attempts - 1:
                raise
        # Backoff protects the public endpoint while keeping bounded retry behavior.
        time.sleep(2**attempt)
    raise RuntimeError("unreachable retry state")


def normalize_candle(value) -> dict[str, str | int]:
    if not isinstance(value, list) or len(value) != 6:
        raise ValueError("Coinbase candles must contain time, low, high, open, close, and volume")
    timestamp, low, high, open_price, close, volume = value
    try:
        normalized_time = int(timestamp)
        values = [str(item) for item in (low, high, open_price, close, volume)]
        for item in values:
            float(item)
    except (TypeError, ValueError) as exc:
        raise ValueError("Coinbase candle values must be numeric") from exc
    return {
        "time": normalized_time,
        "low": values[0],
        "high": values[1],
        "open": values[2],
        "close": values[3],
        "volume": values[4],
    }


def write_csv(path: Path, product_id: str, granularity: int, candles: list[dict[str, str | int]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=CSV_FIELDS)
        writer.writeheader()
        timeframe = timeframe_name(granularity)
        for candle in candles:
            writer.writerow(
                {
                    "symbol": product_id,
                    "timeframe": timeframe,
                    "openTime": render_datetime(datetime.fromtimestamp(int(candle["time"]), tz=UTC)),
                    "open": candle["open"],
                    "high": candle["high"],
                    "low": candle["low"],
                    "close": candle["close"],
                    "volume": candle["volume"],
                }
            )


def write_provenance(
    path: Path,
    *,
    output: Path,
    product_id: str,
    start: datetime,
    end: datetime,
    granularity: int,
    candles: list[dict[str, str | int]],
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "provider": "coinbase-exchange",
        "endpoint": COINBASE_CANDLES_URL,
        "retrievedAt": datetime.now(UTC).isoformat(),
        "productId": product_id,
        "startInclusive": render_datetime(start),
        "endExclusive": render_datetime(end),
        "granularitySeconds": granularity,
        "rowCount": len(candles),
        "missingIntervals": missing_intervals(candles, granularity),
        "csv": {"path": str(output), "sha256": sha256_file(output)},
        "note": "Missing intervals are reported and are never synthesized.",
    }
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def missing_intervals(candles: list[dict[str, str | int]], granularity: int) -> list[dict[str, str | int]]:
    gaps = []
    for previous, current in zip(candles, candles[1:]):
        previous_time = int(previous["time"])
        current_time = int(current["time"])
        missing_count = max(0, (current_time - previous_time) // granularity - 1)
        if missing_count:
            gaps.append(
                {
                    "after": render_datetime(datetime.fromtimestamp(previous_time, tz=UTC)),
                    "before": render_datetime(datetime.fromtimestamp(current_time, tz=UTC)),
                    "missingCount": missing_count,
                }
            )
    return gaps


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def timeframe_name(granularity: int) -> str:
    names = {60: "1m", 300: "5m", 900: "15m", 3600: "1h", 21600: "6h", 86400: "1d"}
    return names[granularity]


def render_datetime(value: datetime) -> str:
    return value.astimezone(UTC).isoformat().replace("+00:00", "Z")


if __name__ == "__main__":
    main()
