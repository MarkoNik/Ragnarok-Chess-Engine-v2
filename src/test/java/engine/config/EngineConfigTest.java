package engine.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EngineConfigTest {

    @Test
    void allKnownFlags_defaultToEnabled() {
        EngineConfig config = new EngineConfig();
        for (String name : config.optionNames()) {
            assertTrue(config.isEnabled(name), name + " should default to enabled");
        }
    }

    @Test
    void optionNames_coversTheExpectedFlags() {
        EngineConfig config = new EngineConfig();
        Set<String> names = new HashSet<>();
        config.optionNames().forEach(names::add);
        assertEquals(Set.of("NullMovePruning", "TranspositionTable", "QuiescenceSearch", "PieceSquareTables"), names);
    }

    @Test
    void set_updatesTheFlag() {
        EngineConfig config = new EngineConfig();
        config.set("NullMovePruning", false);
        assertFalse(config.isEnabled("NullMovePruning"));
        // unrelated flags stay at their default
        assertTrue(config.isEnabled("TranspositionTable"));
    }

    @Test
    void set_withStringValue_parsesBoolean() {
        EngineConfig config = new EngineConfig();
        config.set("QuiescenceSearch", "false");
        assertFalse(config.isEnabled("QuiescenceSearch"));
        config.set("QuiescenceSearch", "true");
        assertTrue(config.isEnabled("QuiescenceSearch"));
    }

    @Test
    void set_withUnrecognizedStringValue_isTreatedAsFalse() {
        // matches Boolean.parseBoolean's behavior: anything other than "true" (case-insensitive) is false
        EngineConfig config = new EngineConfig();
        config.set("QuiescenceSearch", "yes");
        assertFalse(config.isEnabled("QuiescenceSearch"));
    }

    @Test
    void isEnabled_unknownOption_throws() {
        EngineConfig config = new EngineConfig();
        assertThrows(IllegalArgumentException.class, () -> config.isEnabled("NotARealOption"));
    }

    @Test
    void set_unknownOption_throws() {
        EngineConfig config = new EngineConfig();
        assertThrows(IllegalArgumentException.class, () -> config.set("NotARealOption", false));
    }

    @Test
    void loadFromFile_overridesOnlySpecifiedFlags(@TempDir Path tempDir) throws IOException {
        Path propsFile = tempDir.resolve("profile.properties");
        Files.writeString(propsFile, "NullMovePruning=false\nPieceSquareTables=false\n");

        EngineConfig config = EngineConfig.loadFromFile(propsFile);

        assertFalse(config.isEnabled("NullMovePruning"));
        assertFalse(config.isEnabled("PieceSquareTables"));
        // untouched flags keep their defaults
        assertTrue(config.isEnabled("TranspositionTable"));
        assertTrue(config.isEnabled("QuiescenceSearch"));
    }

    @Test
    void loadFromFile_emptyFile_leavesAllDefaults(@TempDir Path tempDir) throws IOException {
        Path propsFile = tempDir.resolve("empty.properties");
        Files.writeString(propsFile, "");

        EngineConfig config = EngineConfig.loadFromFile(propsFile);

        for (String name : config.optionNames()) {
            assertTrue(config.isEnabled(name));
        }
    }

    @Test
    void loadFromFile_unknownKeysInFile_areIgnored(@TempDir Path tempDir) throws IOException {
        Path propsFile = tempDir.resolve("profile.properties");
        Files.writeString(propsFile, "SomeFutureFlagNotYetSupported=false\n");

        EngineConfig config = EngineConfig.loadFromFile(propsFile);

        for (String name : config.optionNames()) {
            assertTrue(config.isEnabled(name));
        }
    }
}
