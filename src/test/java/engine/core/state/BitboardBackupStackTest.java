package engine.core.state;

import app.Constants;
import engine.util.bits.FenParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Bitboard's backup/restore stack is a fixed-size array. A caller mismatch
 * (backing up more than restoring, or vice versa) used to corrupt memory via
 * a raw ArrayIndexOutOfBoundsException with no indication of the real cause -
 * see MinimaxTest's getPrincipalVariation regression test for a real example
 * that crashed the engine this way. These checks turn that into a clear,
 * diagnosable failure.
 */
class BitboardBackupStackTest {
    // Must match Bitboard's private BACKUP_STACK_SIZE.
    private static final int BACKUP_STACK_SIZE = 100_000;

    @Test
    void restoreState_withoutPriorBackup_throwsClearException() {
        Bitboard bitboard = FenParser.parseFEN(Constants.INITIAL_FEN).getBitboard();
        assertThrows(IllegalStateException.class, bitboard::restoreState);
    }

    @Test
    void backupState_beyondStackCapacity_throwsClearExceptionInsteadOfCorruptingMemory() {
        Bitboard bitboard = FenParser.parseFEN(Constants.INITIAL_FEN).getBitboard();
        for (int i = 0; i < BACKUP_STACK_SIZE; i++) {
            bitboard.backupState();
        }
        assertThrows(IllegalStateException.class, bitboard::backupState);
    }
}
