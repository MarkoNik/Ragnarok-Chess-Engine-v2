package uci.command;

import engine.core.state.EngineState;
import uci.Cli;

public class UciCommand implements Command {
    public int execute(EngineState engineState) {
        Cli.sendCommand("id name Ragnarok");
        Cli.sendCommand("id author MarkoNik");
        for (String name : engineState.getConfig().optionNames()) {
            Cli.sendCommand("option name " + name + " type check default " + engineState.getConfig().isEnabled(name));
        }
        Cli.sendCommand("uciok");
        return 0;
    }
}
