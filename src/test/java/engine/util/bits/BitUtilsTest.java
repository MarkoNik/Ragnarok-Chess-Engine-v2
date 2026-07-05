package engine.util.bits;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class BitUtilsTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7, 8, 27, 62, 63})
    void getBit_isFalseOnEmptyBitboard(int square) {
        assertFalse(BitUtils.getBit(0L, square));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7, 8, 27, 62, 63})
    void setBit_thenGetBit_isTrueOnlyForThatSquare(int square) {
        long bitboard = BitUtils.setBit(0L, square);
        for (int i = 0; i < 64; i++) {
            assertEquals(i == square, BitUtils.getBit(bitboard, i), "square " + i);
        }
    }

    @Test
    void setBit_isIdempotent() {
        long bitboard = BitUtils.setBit(0L, 10);
        bitboard = BitUtils.setBit(bitboard, 10);
        assertEquals(1L << 10, bitboard);
    }

    @Test
    void setBit_preservesOtherBits() {
        long bitboard = BitUtils.setBit(0L, 3);
        bitboard = BitUtils.setBit(bitboard, 40);
        assertTrue(BitUtils.getBit(bitboard, 3));
        assertTrue(BitUtils.getBit(bitboard, 40));
        assertEquals((1L << 3) | (1L << 40), bitboard);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7, 8, 27, 62, 63})
    void popBit_clearsOnlyThatSquare(int square) {
        long full = -1L; // all bits set
        long result = BitUtils.popBit(full, square);
        for (int i = 0; i < 64; i++) {
            assertEquals(i != square, BitUtils.getBit(result, i), "square " + i);
        }
    }

    @Test
    void popBit_onUnsetSquare_isNoOp() {
        long bitboard = BitUtils.setBit(0L, 5);
        long result = BitUtils.popBit(bitboard, 6);
        assertEquals(bitboard, result);
    }

    @Test
    void popBit_onEmptyBitboard_isNoOp() {
        assertEquals(0L, BitUtils.popBit(0L, 0));
    }

    @Test
    void getLs1bIndex_onEmptyBitboard_returnsMinusOne() {
        assertEquals(-1, BitUtils.getLs1bIndex(0L));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7, 8, 27, 62, 63})
    void getLs1bIndex_singleBit_returnsThatIndex(int square) {
        long bitboard = 1L << square;
        assertEquals(square, BitUtils.getLs1bIndex(bitboard));
    }

    @Test
    void getLs1bIndex_multipleBits_returnsLowestIndex() {
        long bitboard = (1L << 40) | (1L << 12) | (1L << 63);
        assertEquals(12, BitUtils.getLs1bIndex(bitboard));
    }

    @Test
    void getLs1bIndex_allBitsSet_returnsZero() {
        assertEquals(0, BitUtils.getLs1bIndex(-1L));
    }
}
