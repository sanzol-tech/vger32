package ar.vger32app.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/*
 * Unit tests for TrivialCodeValidator.isTrivialCode().
 * Trivial: null, empty, wrong length, all-equal digits, strictly ascending or descending.
 */
public class TrivialCodeValidatorTest {

    // --------------------------------------------------------
    // --- ALL DIGITS EQUAL -----------------------------------

    @Test
    public void isTrivialCode_allZeros_returnsTrue() {
        assertTrue(TrivialCodeValidator.isTrivialCode("000000"));
    }

    @Test
    public void isTrivialCode_allOnes_returnsTrue() {
        assertTrue(TrivialCodeValidator.isTrivialCode("111111"));
    }

    @Test
    public void isTrivialCode_allNines_returnsTrue() {
        assertTrue(TrivialCodeValidator.isTrivialCode("999999"));
    }

    @Test
    public void isTrivialCode_mixedDigits_returnsFalse() {
        assertFalse(TrivialCodeValidator.isTrivialCode("281943"));
    }

    // --------------------------------------------------------
    // --- SEQUENTIAL ASCENDING -------------------------------

    @Test
    public void isTrivialCode_ascendingFrom0_returnsTrue() {
        assertTrue(TrivialCodeValidator.isTrivialCode("012345"));
    }

    @Test
    public void isTrivialCode_ascendingFrom1_returnsTrue() {
        assertTrue(TrivialCodeValidator.isTrivialCode("123456"));
    }

    @Test
    public void isTrivialCode_ascendingFrom2_returnsTrue() {
        assertTrue(TrivialCodeValidator.isTrivialCode("234567"));
    }

    @Test
    public void isTrivialCode_ascendingFrom4_returnsTrue() {
        assertTrue(TrivialCodeValidator.isTrivialCode("456789"));
    }

    @Test
    public void isTrivialCode_ascendingWithWrap_returnsFalse() {
        // wrap-around 9→0 is not +1, so not sequential
        assertFalse(TrivialCodeValidator.isTrivialCode("789012"));
    }

    @Test
    public void isTrivialCode_ascendingWithGap_returnsFalse() {
        assertFalse(TrivialCodeValidator.isTrivialCode("123568"));
    }

    @Test
    public void isTrivialCode_ascendingNonConsecutive_returnsFalse() {
        // gaps of 2, not strictly +1
        assertFalse(TrivialCodeValidator.isTrivialCode("135791"));
    }

    // --------------------------------------------------------
    // --- SEQUENTIAL DESCENDING ------------------------------

    @Test
    public void isTrivialCode_descendingFrom9_returnsTrue() {
        assertTrue(TrivialCodeValidator.isTrivialCode("987654"));
    }

    @Test
    public void isTrivialCode_descendingFrom8_returnsTrue() {
        assertTrue(TrivialCodeValidator.isTrivialCode("876543"));
    }

    @Test
    public void isTrivialCode_descendingFrom6_returnsTrue() {
        assertTrue(TrivialCodeValidator.isTrivialCode("654321"));
    }

    @Test
    public void isTrivialCode_descendingFrom5_returnsTrue() {
        assertTrue(TrivialCodeValidator.isTrivialCode("543210"));
    }

    @Test
    public void isTrivialCode_descendingWithWrap_returnsFalse() {
        assertFalse(TrivialCodeValidator.isTrivialCode("321098"));
    }

    @Test
    public void isTrivialCode_descendingWithGap_returnsFalse() {
        // gap at 8→6, not strictly -1
        assertFalse(TrivialCodeValidator.isTrivialCode("986543"));
    }

    // --------------------------------------------------------
    // --- INVALID INPUT --------------------------------------

    @Test
    public void isTrivialCode_null_returnsTrue() {
        assertTrue(TrivialCodeValidator.isTrivialCode(null));
    }

    @Test
    public void isTrivialCode_emptyString_returnsTrue() {
        assertTrue(TrivialCodeValidator.isTrivialCode(""));
    }

    @Test
    public void isTrivialCode_length5_returnsFalse() {
        assertFalse(TrivialCodeValidator.isTrivialCode("12345"));
    }

    @Test
    public void isTrivialCode_length7_returnsFalse() {
        assertFalse(TrivialCodeValidator.isTrivialCode("1234567"));
    }

    // --------------------------------------------------------
    // --- NON-TRIVIAL CODES ----------------------------------

    @Test
    public void isTrivialCode_randomCode_returnsFalse() {
        assertFalse(TrivialCodeValidator.isTrivialCode("137924"));
    }

    @Test
    public void isTrivialCode_alternating_returnsFalse() {
        // Repeating patterns are not flagged as trivial — only all-equal and strictly sequential
        assertFalse(TrivialCodeValidator.isTrivialCode("121212"));
    }

    @Test
    public void isTrivialCode_palindrome_returnsFalse() {
        assertFalse(TrivialCodeValidator.isTrivialCode("123321"));
    }

    @Test
    public void isTrivialCode_birthdayCode_returnsFalse() {
        assertFalse(TrivialCodeValidator.isTrivialCode("150892"));
    }

    // --------------------------------------------------------
    // --- BOUNDARY TESTS -------------------------------------

    @Test
    public void isTrivialCode_almostSequential_oneOff_returnsFalse() {
        assertFalse(TrivialCodeValidator.isTrivialCode("123457"));
    }

    @Test
    public void isTrivialCode_withJumps_returnsFalse() {
        assertFalse(TrivialCodeValidator.isTrivialCode("124800"));
    }

    @Test
    public void isTrivialCode_nearlyAllEqual_returnsFalse() {
        // five equal digits + one different: not all-same, not sequential
        assertFalse(TrivialCodeValidator.isTrivialCode("111112"));
    }
}