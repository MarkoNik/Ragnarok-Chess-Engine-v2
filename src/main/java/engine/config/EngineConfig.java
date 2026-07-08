package engine.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Feature flags consulted by search/eval, so individual features can be
 * toggled off one at a time to measure their actual contribution to playing
 * strength via bench/run_match.py, rather than assuming it.
 *
 * Controlled two ways, layered:
 *  - A java.util.Properties file loaded once at startup via
 *    -Dengine.config=<path>, for reusable named profiles (see bench/configs/).
 *  - UCI "setoption name <Flag> value true/false" at runtime (see UciCommand/
 *    SetOptionCommand) - what bench/run_match.py's --engine-*-options sends.
 *    Always wins over the config file, since it's applied after startup.
 *
 * New flags: add one line to the constructor's defaults map. UciCommand,
 * SetOptionCommand, and file loading all work off that map automatically.
 */
public class EngineConfig {
    private final Map<String, Boolean> flags = new LinkedHashMap<>();

    public EngineConfig() {
        flags.put("NullMovePruning", true);
        flags.put("TranspositionTable", true);
        flags.put("QuiescenceSearch", true);
        flags.put("PieceSquareTables", true);
    }

    public boolean isEnabled(String name) {
        Boolean value = flags.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Unknown engine option: " + name);
        }
        return value;
    }

    public void set(String name, boolean value) {
        if (!flags.containsKey(name)) {
            throw new IllegalArgumentException("Unknown engine option: " + name);
        }
        flags.put(name, value);
    }

    /** Parses "true"/"false" (case-insensitive); anything else is treated as false, matching Boolean.parseBoolean. */
    public void set(String name, String value) {
        set(name, Boolean.parseBoolean(value));
    }

    public Iterable<String> optionNames() {
        return Collections.unmodifiableSet(flags.keySet());
    }

    public static EngineConfig loadFromFile(Path path) throws IOException {
        EngineConfig config = new EngineConfig();
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        }
        for (String name : config.flags.keySet()) {
            String raw = properties.getProperty(name);
            if (raw != null) {
                config.set(name, raw);
            }
        }
        return config;
    }
}
