#!/usr/bin/env python3
"""Print bench/history.jsonl as a readable table of checkpoints over time.

Usage: python print_history.py [path/to/history.jsonl]
"""
import json
import pathlib
import sys

DEFAULT_HISTORY_FILE = pathlib.Path(__file__).parent / "history.jsonl"


def fmt_result(r):
    if r is None or r.get("elo_diff") is None:
        return "n/a"
    return (f"{r['wins']}W {r['draws']}D {r['losses']}L   "
            f"Elo {r['elo_diff']:+.0f} [{r['elo_diff_lo']:+.0f}, {r['elo_diff_hi']:+.0f}]")


def main():
    history_file = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_HISTORY_FILE
    if not history_file.exists():
        print(f"no history file at {history_file}")
        return 1

    lines = [line for line in history_file.read_text().splitlines() if line.strip()]
    if not lines:
        print(f"{history_file} is empty")
        return 0

    for line in lines:
        record = json.loads(line)
        print(f"{record['timestamp']}  {record['ref_new_sha'][:8]} ({record['ref_new']}) vs {record['ref_prev']}")
        print(f"  vs prev:                    {fmt_result(record['vs_prev'])}")
        print(f"  vs Stockfish {record['stockfish_elo_a']:<4}:       {fmt_result(record['vs_stockfish_a'])}")
        print(f"  vs Stockfish {record['stockfish_elo_b']:<4}:       {fmt_result(record['vs_stockfish_b'])}")
        print()

    return 0


if __name__ == "__main__":
    sys.exit(main())
