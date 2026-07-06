"""Score -> Elo conversion shared by run_match.py and aggregate_results.py."""
import math


def elo_diff_with_error(wins, draws, losses):
    """Standard score->Elo conversion with a 95% CI from the sample variance
    of individual game outcomes (1/0.5/0), not a naive binomial approximation.
    Returns (elo, elo_lo, elo_hi), or (None, None, None) if no games were played."""
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
