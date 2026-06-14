package ar.vger32app.localizer;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/*
 * Unit tests for Waypoint.isValidName().
 * Valid names: 1–6 chars, [A-Z0-9] only.
 */
public class WaypointNameTest {

    // --------------------------------------------------------
    // --- VALID ----------------------------------------------

    @Test
    public void isValidName_singleChar_returnsTrue() {
        assertTrue(Waypoint.isValidName("A"));
    }

    @Test
    public void isValidName_maxLength_returnsTrue() {
        assertTrue(Waypoint.isValidName("ABC123"));
    }

    @Test
    public void isValidName_digitsOnly_returnsTrue() {
        assertTrue(Waypoint.isValidName("123456"));
    }

    @Test
    public void isValidName_lettersOnly_returnsTrue() {
        assertTrue(Waypoint.isValidName("ROOM"));
    }

    // --------------------------------------------------------
    // --- INVALID — length -----------------------------------

    @Test
    public void isValidName_null_returnsFalse() {
        assertFalse(Waypoint.isValidName(null));
    }

    @Test
    public void isValidName_empty_returnsFalse() {
        assertFalse(Waypoint.isValidName(""));
    }

    @Test
    public void isValidName_tooLong_returnsFalse() {
        assertFalse(Waypoint.isValidName("ABCDEF1"));
    }

    // --------------------------------------------------------
    // --- INVALID — charset ----------------------------------

    @Test
    public void isValidName_lowercase_returnsFalse() {
        assertFalse(Waypoint.isValidName("abc"));
    }

    @Test
    public void isValidName_space_returnsFalse() {
        assertFalse(Waypoint.isValidName("AB CD"));
    }

    @Test
    public void isValidName_dash_returnsFalse() {
        assertFalse(Waypoint.isValidName("AB-CD"));
    }

    @Test
    public void isValidName_underscore_returnsFalse() {
        assertFalse(Waypoint.isValidName("AB_CD"));
    }
}