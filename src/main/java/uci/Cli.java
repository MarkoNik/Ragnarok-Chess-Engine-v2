package uci;

import app.UciLogger;
import engine.core.state.EngineState;
import uci.command.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public class Cli implements Runnable {
    private static final BufferedReader cliInput = new BufferedReader(new InputStreamReader(System.in));
    private static final PrintWriter cliOutput = new PrintWriter(new OutputStreamWriter(System.out), true);
    private final EngineState engineState;

    public Cli(EngineState engineState) {
        this.engineState = engineState;
    }

    public void run() {
        try {
            String line;
            while ((line = cliInput.readLine()) != null) {
                UciLogger.info("Received command from GUI: " + line);
                int result = handleCommand(line);
                if (result == -1) {
                    UciLogger.info("Stopping UCI backend!");
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int handleCommand(String line) {
        return resolveCommand(line).execute(engineState);
    }

    /**
     * Routing is by prefix match, so ordering matters whenever one command's
     * name is itself a prefix of another's (e.g. "uci" is a prefix of
     * "ucinewgame"). "uci" is matched by exact equality specifically to avoid
     * swallowing "ucinewgame" - that collision used to make "ucinewgame"
     * silently re-trigger the full "uci" identification handshake
     * (id name/id author/uciok) instead of resetting game state.
     */
    static Command resolveCommand(String line) {
        if (line.equals("uci")) {
            return new UciCommand();
        }
        else if (line.startsWith("debug")) {
            return new DebugCommand(line);
        }
        else if (line.startsWith("isready")) {
            return new IsReadyCommand();
        }
        else if (line.startsWith("setoption")) {
            return new SetOptionCommand(line);
        }
        else if (line.startsWith("register")) {
            return new RegisterCommand();
        }
        else if (line.startsWith("ucinewgame")) {
            return new UciNewGameCommand();
        }
        else if (line.startsWith("position")) {
            return new PositionCommand(line);
        }
        else if (line.startsWith("go")) {
            return new GoCommand(line);
        }
        else if (line.startsWith("stop")) {
            return new StopCommand();
        }
        else if (line.startsWith("ponderhit")) {
            return new PonderHitCommand();
        }
        else if (line.startsWith("quit")) {
            return new QuitCommand();
        }
        else if (line.startsWith("make")) {
            return new MakeMoveCommand(line);
        }
        else if (line.startsWith("unmake")) {
            return new UnmakeMoveCommand();
        }
        return new NullCommand();
    }

    public static void sendCommand(String command) {
        UciLogger.info("Sending command to GUI: " + command);
        cliOutput.println(command);
    }
}
