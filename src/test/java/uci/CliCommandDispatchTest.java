package uci;

import org.junit.jupiter.api.Test;
import uci.command.*;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Cli.resolveCommand dispatches by prefix match, so a command whose name is a
 * prefix of another's needs care about ordering/exact-match. "uci" used to be
 * matched with startsWith, which also matches "ucinewgame" - so every
 * "ucinewgame" was silently handled as a second "uci" handshake instead of
 * resetting game state. This locks in correct routing for every known
 * command, including that specific collision.
 */
class CliCommandDispatchTest {

    @Test
    void uci_matchesOnlyExactly() {
        assertInstanceOf(UciCommand.class, Cli.resolveCommand("uci"));
    }

    @Test
    void ucinewgame_isNotSwallowedByUciPrefix() {
        assertInstanceOf(UciNewGameCommand.class, Cli.resolveCommand("ucinewgame"));
    }

    @Test
    void debug_dispatchesCorrectly() {
        assertInstanceOf(DebugCommand.class, Cli.resolveCommand("debug on"));
    }

    @Test
    void isready_dispatchesCorrectly() {
        assertInstanceOf(IsReadyCommand.class, Cli.resolveCommand("isready"));
    }

    @Test
    void setoption_dispatchesCorrectly() {
        assertInstanceOf(SetOptionCommand.class, Cli.resolveCommand("setoption name Hash value 64"));
    }

    @Test
    void register_dispatchesCorrectly() {
        assertInstanceOf(RegisterCommand.class, Cli.resolveCommand("register"));
    }

    @Test
    void position_dispatchesCorrectly() {
        assertInstanceOf(PositionCommand.class, Cli.resolveCommand("position startpos"));
    }

    @Test
    void go_dispatchesCorrectly() {
        assertInstanceOf(GoCommand.class, Cli.resolveCommand("go movetime 100"));
    }

    @Test
    void stop_dispatchesCorrectly() {
        assertInstanceOf(StopCommand.class, Cli.resolveCommand("stop"));
    }

    @Test
    void ponderhit_dispatchesCorrectly() {
        assertInstanceOf(PonderHitCommand.class, Cli.resolveCommand("ponderhit"));
    }

    @Test
    void quit_dispatchesCorrectly() {
        assertInstanceOf(QuitCommand.class, Cli.resolveCommand("quit"));
    }

    @Test
    void make_dispatchesCorrectly() {
        assertInstanceOf(MakeMoveCommand.class, Cli.resolveCommand("make e2e4"));
    }

    @Test
    void unmake_isNotSwallowedByMakePrefix() {
        assertInstanceOf(UnmakeMoveCommand.class, Cli.resolveCommand("unmake"));
    }

    @Test
    void unrecognizedCommand_resolvesToNullCommand() {
        assertInstanceOf(NullCommand.class, Cli.resolveCommand("totally-not-a-uci-command"));
    }
}
