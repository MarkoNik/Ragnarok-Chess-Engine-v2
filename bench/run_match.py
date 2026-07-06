#!/usr/bin/env python3
"""Play an engine-vs-engine match over UCI and report W/D/L + an Elo estimate.

Used to measure whether a change to the engine actually helped: run the old
build against the new build (self-play A/B), or run against Stockfish with
UCI_LimitStrength/UCI_Elo as a calibrated external reference point.

Example - self-play A/B between two jars:
    .venv/bin/python run_match.py \\
        --engine-a "java -jar old.jar" --engine-a-name old \\
        --engine-b "java -jar new.jar" --engine-b-name new \\
        --games 200 --movetime 200

Example - vs. a calibrated Stockfish:
    .venv/bin/python run_match.py \\
        --engine-a "java -jar target/my-project-1.0-SNAPSHOT.jar" --engine-a-name ragnarok \\
        --engine-b stockfish --engine-b-name "stockfish-1400" \\
        --engine-b-options "UCI_LimitStrength=true,UCI_Elo=1400" \\
        --games 200 --movetime 200
"""
import argparse
import datetime
import logging
import math
import pathlib
import shlex
import sys

import chess
import chess.engine
import chess.pgn

# python-chess logs every raw engine I/O line (and warns on any line it
# doesn't recognize, e.g. if an engine ever logs to stdout) at a level that's
# visible by default. That's useful when debugging one engine interactively,
# but drowns out this script's own progress output across a batch of games.
logging.getLogger("chess.engine").setLevel(logging.WARNING)

DEFAULT_BOOK = pathlib.Path(__file__).parent / "openings.txt"


def load_book(path):
    lines = []
    for raw in pathlib.Path(path).read_text().splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        lines.append(line.split())
    if not lines:
        raise ValueError(f"no openings found in {path}")
    return lines


def parse_options(spec):
    """Parses "K=V,K=V" into a dict, coercing true/false/ints where obvious."""
    options = {}
    if not spec:
        return options
    for pair in spec.split(","):
        key, _, value = pair.partition("=")
        key = key.strip()
        value = value.strip()
        if value.lower() in ("true", "false"):
            value = value.lower() == "true"
        else:
            try:
                value = int(value)
            except ValueError:
                pass
        options[key] = value
    return options


def elo_diff_with_error(wins, draws, losses):
    """Standard score->Elo conversion with a 95% CI from the sample variance
    of individual game outcomes (1/0.5/0), not a naive binomial approximation.
    Returns (elo, elo_lo, elo_hi), any of which may be None if undefined
    (all-wins or all-losses put the point estimate at +/-infinity)."""
    n = wins + draws + losses
    if n == 0:
        return None, None, None

    p = (wins + 0.5 * draws) / n
    variance = (wins * (1 - p) ** 2 + draws * (0.5 - p) ** 2 + losses * (0 - p) ** 2) / n
    stderr = math.sqrt(variance / n) if n > 0 else 0.0

    def to_elo(score):
        score = min(max(score, 1e-6), 1 - 1e-6)
        return -400 * math.log10(1 / score - 1)

    elo = to_elo(p)
    elo_lo = to_elo(p - 1.95996 * stderr)
    elo_hi = to_elo(p + 1.95996 * stderr)
    return elo, elo_lo, elo_hi


def play_game(engine_white, engine_black, book_line, movetime_ms, max_plies):
    board = chess.Board()
    for uci in book_line:
        board.push_uci(uci)

    limit = chess.engine.Limit(time=movetime_ms / 1000)
    aborted_reason = None

    while not board.is_game_over(claim_draw=True):
        if board.ply() >= max_plies:
            aborted_reason = f"move cap ({max_plies} plies) reached"
            break

        engine = engine_white if board.turn == chess.WHITE else engine_black
        mover_name = "white" if board.turn == chess.WHITE else "black"
        try:
            result = engine.play(board, limit)
        except chess.engine.EngineError as e:
            aborted_reason = f"{mover_name} engine error: {e}"
            break

        if result.move is None:
            aborted_reason = f"{mover_name} engine returned no move"
            break
        board.push(result.move)

    game = chess.pgn.Game.from_board(board)
    if aborted_reason:
        game.headers["Termination"] = aborted_reason
    return board, game, aborted_reason


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--engine-a", required=True, help="shell command to launch engine A")
    parser.add_argument("--engine-b", required=True, help="shell command to launch engine B")
    parser.add_argument("--engine-a-name", default="EngineA")
    parser.add_argument("--engine-b-name", default="EngineB")
    parser.add_argument("--engine-a-options", default="", help="comma-separated K=V UCI options for engine A")
    parser.add_argument("--engine-b-options", default="", help="comma-separated K=V UCI options for engine B")
    parser.add_argument("--games", type=int, default=10, help="total games to play (split as evenly as possible between colors)")
    parser.add_argument("--movetime", type=int, default=200, help="ms per move for both engines")
    parser.add_argument("--book", default=str(DEFAULT_BOOK), help="path to opening book (one UCI move sequence per line)")
    parser.add_argument("--max-plies", type=int, default=300, help="safety cap; games still going at this ply count are aborted as a result of neither side")
    parser.add_argument("--pgn-out", default=None, help="path to write all games as PGN (default: bench/results/<timestamp>.pgn)")
    args = parser.parse_args()

    book = load_book(args.book)

    pgn_out = pathlib.Path(args.pgn_out) if args.pgn_out else (
        pathlib.Path(__file__).parent / "results" / f"{datetime.datetime.now():%Y%m%d-%H%M%S}.pgn"
    )
    pgn_out.parent.mkdir(parents=True, exist_ok=True)

    engine_a = chess.engine.SimpleEngine.popen_uci(shlex.split(args.engine_a))
    engine_b = chess.engine.SimpleEngine.popen_uci(shlex.split(args.engine_b))
    try:
        options_a = parse_options(args.engine_a_options)
        options_b = parse_options(args.engine_b_options)
        if options_a:
            engine_a.configure(options_a)
        if options_b:
            engine_b.configure(options_b)

        a_wins = a_draws = a_losses = 0
        aborted = 0

        with open(pgn_out, "w") as pgn_file:
            for i in range(args.games):
                book_line = book[i % len(book)]
                a_is_white = (i % 2 == 0)
                white, black = (engine_a, engine_b) if a_is_white else (engine_b, engine_a)
                white_name, black_name = (args.engine_a_name, args.engine_b_name) if a_is_white else (args.engine_b_name, args.engine_a_name)

                board, game, aborted_reason = play_game(white, black, book_line, args.movetime, args.max_plies)

                game.headers["White"] = white_name
                game.headers["Black"] = black_name
                game.headers["Round"] = str(i + 1)
                print(game, file=pgn_file, end="\n\n")

                result = board.result(claim_draw=True) if not aborted_reason else "*"
                if aborted_reason:
                    aborted += 1
                    outcome = "aborted"
                elif result == "1-0":
                    outcome = "A win" if a_is_white else "A loss"
                    a_wins += a_is_white
                    a_losses += not a_is_white
                elif result == "0-1":
                    outcome = "A loss" if a_is_white else "A win"
                    a_losses += a_is_white
                    a_wins += not a_is_white
                else:
                    outcome = "draw"
                    a_draws += 1

                print(f"game {i + 1:>4}/{args.games}  {white_name:>12} vs {black_name:<12}  "
                      f"result={result:<5} {outcome}" + (f"  ({aborted_reason})" if aborted_reason else ""))
    finally:
        engine_a.quit()
        engine_b.quit()

    played = a_wins + a_draws + a_losses
    print()
    print(f"=== {args.engine_a_name} vs {args.engine_b_name} ({played} decisive/drawn games, {aborted} aborted) ===")
    print(f"{args.engine_a_name}: {a_wins}W {a_draws}D {a_losses}L  "
          f"score={a_wins + 0.5 * a_draws:.1f}/{played} ({(a_wins + 0.5 * a_draws) / played * 100:.1f}%)" if played else "no completed games")

    if played:
        elo, elo_lo, elo_hi = elo_diff_with_error(a_wins, a_draws, a_losses)
        print(f"Elo diff ({args.engine_a_name} - {args.engine_b_name}): {elo:+.0f} "
              f"[95% CI {elo_lo:+.0f}, {elo_hi:+.0f}]")

    print(f"PGN written to {pgn_out}")


if __name__ == "__main__":
    sys.exit(main())
