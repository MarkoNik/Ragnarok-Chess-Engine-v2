package engine.search;

import app.EngineLogger;

public class SearchStats {
    private int nodesSearched = 0;
    private int betaCutoffs = 0;
    private int bestMoves = 0;
    private int checkmates = 0;
    private int stalemates = 0;
    private int exacts = 0;
    private int lowerBounds = 0;
    private int upperBounds = 0;
    private int ttCutoffs = 0;
    private int nullMoveCutoffs = 0;
    private int depth = 0;

    public void recordNodeSearched() {
        nodesSearched++;
    }

    public void recordBetaCutoff() {
        betaCutoffs++;
    }

    public void recordBestMove() {
        bestMoves++;
    }

    public void recordCheckmate() {
        checkmates++;
    }

    public void recordStalemate() {
        stalemates++;
    }

    public void recordExact() {
        exacts++;
    }

    public void recordLowerBound() {
        lowerBounds++;
    }

    public void recordUpperBound() {
        upperBounds++;
    }

    public void recordTTCutoff() {
        ttCutoffs++;
    }

    public void recordNullMoveCutoff() {
        nullMoveCutoffs++;
    }

    public void recordDepthIncreased() {
        depth++;
    }

    public void logSearchStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("Search statistics:\n");
        sb.append("-------------------------\n");
        sb.append("Nodes searched  : ").append(nodesSearched).append("\n");
        sb.append("Beta Cutoffs    : ").append(betaCutoffs).append("\n");
        sb.append("Best Moves      : ").append(bestMoves).append("\n");
        sb.append("Checkmates      : ").append(checkmates).append("\n");
        sb.append("Stalemates      : ").append(stalemates).append("\n");
        sb.append("Exacts          : ").append(exacts).append("\n");
        sb.append("Lower Bounds    : ").append(lowerBounds).append("\n");
        sb.append("Upper Bounds    : ").append(upperBounds).append("\n");
        sb.append("TT Cutoffs      : ").append(ttCutoffs).append("\n");
        sb.append("Null Move Cuts  : ").append(nullMoveCutoffs).append("\n");
        sb.append("Search Depth    : ").append(depth).append("\n\n");
        EngineLogger.debug(sb.toString());
    }
}
