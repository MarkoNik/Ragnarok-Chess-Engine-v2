package engine.core.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TranspositionTableTest {

    // Must match TranspositionTable's private SIZE (4 * 1048576) so we can construct
    // deliberate index collisions between distinct keys.
    private static final long TABLE_SIZE = 4L * 1048576L;

    @Test
    void get_onEmptyTable_returnsNull() {
        TranspositionTable tt = new TranspositionTable();
        assertNull(tt.get(12345L));
    }

    @Test
    void put_thenGet_roundTripsAllFields() {
        TranspositionTable tt = new TranspositionTable();
        long hash = 987654321L;
        tt.put(hash, /*bestMove*/ 42, /*score*/ -150, /*depth*/ 6, tt.LOWER_BOUND);

        TranspositionTable.TTEntry entry = tt.get(hash);
        assertNotNull(entry);
        assertEquals(hash, entry.key());
        assertEquals(42, entry.bestMove());
        assertEquals(-150, entry.score());
        assertEquals(6, entry.depth());
        assertEquals(tt.LOWER_BOUND, entry.flag());
    }

    @Test
    void put_overwritesPreviousEntryAtSameKey() {
        TranspositionTable tt = new TranspositionTable();
        long hash = 555L;
        tt.put(hash, 1, 10, 2, tt.EXACT);
        tt.put(hash, 2, 20, 4, tt.UPPER_BOUND);

        TranspositionTable.TTEntry entry = tt.get(hash);
        assertEquals(2, entry.bestMove());
        assertEquals(20, entry.score());
        assertEquals(4, entry.depth());
        assertEquals(tt.UPPER_BOUND, entry.flag());
    }

    @Test
    void get_returnsNullWhenIndexCollidesButKeyDiffers() {
        TranspositionTable tt = new TranspositionTable();
        long hashA = 777L;
        long hashB = hashA + TABLE_SIZE; // same index (hash % SIZE), different key

        tt.put(hashA, 1, 100, 3, tt.EXACT);

        assertNull(tt.get(hashB), "different key mapping to the same slot must not be returned");
        // original entry should be untouched
        assertEquals(hashA, tt.get(hashA).key());
    }

    @Test
    void put_atCollidingIndex_replacesEntryUnconditionally() {
        TranspositionTable tt = new TranspositionTable();
        long hashA = 777L;
        long hashB = hashA + TABLE_SIZE;

        tt.put(hashA, 1, 100, 3, tt.EXACT);
        tt.put(hashB, 2, 200, 1, tt.LOWER_BOUND);

        // always-replace scheme: hashA's entry is gone, slot now belongs to hashB
        assertNull(tt.get(hashA));
        assertNotNull(tt.get(hashB));
        assertEquals(hashB, tt.get(hashB).key());
    }

    @Test
    void negativeHash_roundTripsCorrectly() {
        TranspositionTable tt = new TranspositionTable();
        long hash = -42L; // Long.remainderUnsigned must be used to index correctly
        tt.put(hash, 7, 33, 5, tt.EXACT);

        TranspositionTable.TTEntry entry = tt.get(hash);
        assertNotNull(entry);
        assertEquals(hash, entry.key());
        assertEquals(7, entry.bestMove());
    }

    @Test
    void clear_removesAllEntries() {
        TranspositionTable tt = new TranspositionTable();
        tt.put(1L, 1, 1, 1, tt.EXACT);
        tt.put(2L, 2, 2, 2, tt.EXACT);
        tt.put(3L, 3, 3, 3, tt.EXACT);

        tt.clear();

        assertNull(tt.get(1L));
        assertNull(tt.get(2L));
        assertNull(tt.get(3L));
    }
}
