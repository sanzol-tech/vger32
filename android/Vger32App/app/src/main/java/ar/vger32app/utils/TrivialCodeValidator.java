package ar.vger32app.utils;

/*
 * Detects trivial PIN codes: all digits equal, strictly ascending,
 * or strictly descending. Null and empty inputs are also rejected.
 */

public class TrivialCodeValidator {

    public static boolean isTrivialCode(String code) {
        if (code == null || code.isEmpty()) return true;
        if (code.length() != 6) return false;
        if (allDigitsEqual(code)) return true;
        return isSequential(code);
    }

    private static boolean allDigitsEqual(String code) {
        char firstChar = code.charAt(0);
        for (char c : code.toCharArray()) {
            if (c != firstChar) return false;
        }
        return true;
    }

    private static boolean isSequential(String code) {
        int[] digits = code.chars().map(c -> c - '0').toArray();
        boolean ascending = true;
        boolean descending = true;

        for (int i = 0; i < digits.length - 1; i++) {
            if (digits[i] + 1 != digits[i + 1]) ascending = false;
            if (digits[i] - 1 != digits[i + 1]) descending = false;
        }

        return ascending || descending;
    }
}