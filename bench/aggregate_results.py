#!/usr/bin/env python3
"""Combine JSON summaries from sharded run_match.py invocations into one overall result.

Example:
    python aggregate_results.py shard-results/*/summary.json
"""
import argparse
import glob
import json
import sys

from elo import elo_diff_with_error


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("summaries", nargs="+", help="paths or globs to run_match.py --summary-out JSON files")
    args = parser.parse_args()

    paths = []
    for pattern in args.summaries:
        matched = sorted(glob.glob(pattern))
        paths.extend(matched if matched else [pattern])

    if not paths:
        print("no summary files found", file=sys.stderr)
        return 1

    total_wins = total_draws = total_losses = total_aborted = 0
    engine_a_name = engine_b_name = None
    for path in paths:
        with open(path) as f:
            summary = json.load(f)
        engine_a_name = engine_a_name or summary["engine_a_name"]
        engine_b_name = engine_b_name or summary["engine_b_name"]
        total_wins += summary["a_wins"]
        total_draws += summary["a_draws"]
        total_losses += summary["a_losses"]
        total_aborted += summary["aborted"]

    played = total_wins + total_draws + total_losses
    print(f"=== Aggregated: {engine_a_name} vs {engine_b_name} "
          f"({len(paths)} shards, {played} decisive/drawn games, {total_aborted} aborted) ===")

    if played:
        score = total_wins + 0.5 * total_draws
        print(f"{engine_a_name}: {total_wins}W {total_draws}D {total_losses}L  "
              f"score={score:.1f}/{played} ({score / played * 100:.1f}%)")
        elo, elo_lo, elo_hi = elo_diff_with_error(total_wins, total_draws, total_losses)
        print(f"Elo diff ({engine_a_name} - {engine_b_name}): {elo:+.0f} [95% CI {elo_lo:+.0f}, {elo_hi:+.0f}]")
    else:
        print("no completed games across any shard")

    return 0


if __name__ == "__main__":
    sys.exit(main())
