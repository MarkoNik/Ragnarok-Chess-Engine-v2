#!/usr/bin/env python3
"""Append one checkpoint's results (from checkpoint.yml) to bench/history.jsonl.

Each line is one checkpoint run: new-version-vs-previous plus new-vs-two
Stockfish levels, so Elo progress over time can be read back later with
print_history.py instead of only living in a GitHub Actions job summary.
"""
import argparse
import datetime
import json
import pathlib

DEFAULT_HISTORY_FILE = pathlib.Path(__file__).parent / "history.jsonl"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ref-new", required=True)
    parser.add_argument("--ref-new-sha", required=True)
    parser.add_argument("--ref-prev", required=True)
    parser.add_argument("--stockfish-elo-a", type=int, required=True)
    parser.add_argument("--stockfish-elo-b", type=int, required=True)
    parser.add_argument("--vs-prev-json", required=True, help="aggregate_results.py --json-out for the vs-prev comparison")
    parser.add_argument("--vs-sfa-json", required=True, help="aggregate_results.py --json-out for the vs-Stockfish-A comparison")
    parser.add_argument("--vs-sfb-json", required=True, help="aggregate_results.py --json-out for the vs-Stockfish-B comparison")
    parser.add_argument("--history-file", default=str(DEFAULT_HISTORY_FILE))
    args = parser.parse_args()

    def load(path):
        with open(path) as f:
            return json.load(f)

    record = {
        "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
        "ref_new": args.ref_new,
        "ref_new_sha": args.ref_new_sha,
        "ref_prev": args.ref_prev,
        "stockfish_elo_a": args.stockfish_elo_a,
        "stockfish_elo_b": args.stockfish_elo_b,
        "vs_prev": load(args.vs_prev_json),
        "vs_stockfish_a": load(args.vs_sfa_json),
        "vs_stockfish_b": load(args.vs_sfb_json),
    }

    history_path = pathlib.Path(args.history_file)
    with open(history_path, "a") as f:
        f.write(json.dumps(record) + "\n")

    print(f"Recorded checkpoint to {history_path}")


if __name__ == "__main__":
    main()
