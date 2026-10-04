package ro.mathlms.content;

import java.math.BigInteger;
import java.util.Comparator;

/**
 * "Natural" ordering of names: runs of digits compare as numbers, everything else case-insensitively — so
 * "Capitolul 2" comes before "Capitolul 10" and "Clasa a 9-a" before "Clasa a 10-a" (a plain alphabetical
 * ORDER BY puts the 1x ones first). Used where the app lists classes, books and chapters.
 */
public final class NaturalOrder {

    /** Nulls first; a name before its own longer version; equal numbers with different zero padding keep a stable order. */
    public static final Comparator<String> BY_NAME = NaturalOrder::compare;

    private NaturalOrder() {
    }

    private static int compare(String a, String b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : -1) : 1;
        }
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i);
            char y = b.charAt(j);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int endA = digitsEnd(a, i);
                int endB = digitsEnd(b, j);
                int byNumber = new BigInteger(a.substring(i, endA)).compareTo(new BigInteger(b.substring(j, endB)));
                if (byNumber != 0) {
                    return byNumber;
                }
                i = endA;
                j = endB;
            } else {
                int byChar = Character.compare(Character.toLowerCase(x), Character.toLowerCase(y));
                if (byChar != 0) {
                    return byChar;
                }
                i++;
                j++;
            }
        }
        int byLength = Integer.compare(a.length() - i, b.length() - j);
        return byLength != 0 ? byLength : a.compareTo(b); // total order: "Tema 02" vs "Tema 2" is decided, not tied
    }

    private static int digitsEnd(String s, int from) {
        int end = from;
        while (end < s.length() && Character.isDigit(s.charAt(end))) {
            end++;
        }
        return end;
    }
}
