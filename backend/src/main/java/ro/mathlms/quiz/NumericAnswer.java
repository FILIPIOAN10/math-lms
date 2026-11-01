package ro.mathlms.quiz;

import java.math.BigInteger;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A number as a student writes it, kept as an exact fraction so forms are compared by value: 0,5 = 0.5 = 1/2 = 2/4,
 * 1 1/2 = 3/2, and the periodic 0,(3) = 1/3 of Romanian schoolbooks. Units, radicals and letters are not numbers here:
 * a question whose answer needs them stays a question with a written solution.
 */
public final class NumericAnswer {

    /** The longest text kept: the column's length, and enough for any answer a student writes. */
    public static final int MAX_LENGTH = 40;

    private static final Pattern MIXED = Pattern.compile("(\\d+) +(\\d+) */ *(\\d+)");
    private static final Pattern FRACTION = Pattern.compile("(\\d+) */ *(\\d+)");
    private static final Pattern DECIMAL = Pattern.compile("(\\d*),(\\d*)(?:\\((\\d+)\\))?|(\\d+)");

    private final BigInteger numerator;
    private final BigInteger denominator;

    private NumericAnswer(BigInteger numerator, BigInteger denominator) {
        BigInteger common = numerator.gcd(denominator);
        this.numerator = numerator.divide(common);
        this.denominator = denominator.divide(common);
    }

    /** The value of {@code text}, or empty when it is not a number. */
    public static Optional<NumericAnswer> parse(String text) {
        if (text == null || text.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        String written = text.strip().replace('−', '-').replace('–', '-').replace('.', ',').replace(':', '/');
        boolean negative = written.startsWith("-");
        if (negative || written.startsWith("+")) {
            written = written.substring(1).strip();
        }
        return unsigned(written).map(value -> negative ? value.negated() : value);
    }

    /** Whether two texts are the same number; what is not a number equals nothing. */
    public static boolean same(String one, String other) {
        Optional<NumericAnswer> first = parse(one);
        Optional<NumericAnswer> second = parse(other);
        return first.isPresent() && first.equals(second);
    }

    private static Optional<NumericAnswer> unsigned(String written) {
        Matcher mixed = MIXED.matcher(written);
        if (mixed.matches()) {
            BigInteger below = new BigInteger(mixed.group(3));
            return fraction(new BigInteger(mixed.group(1)).multiply(below).add(new BigInteger(mixed.group(2))), below);
        }
        Matcher fraction = FRACTION.matcher(written);
        if (fraction.matches()) {
            return fraction(new BigInteger(fraction.group(1)), new BigInteger(fraction.group(2)));
        }
        Matcher decimal = DECIMAL.matcher(written);
        if (!decimal.matches()) {
            return Optional.empty();
        }
        if (decimal.group(4) != null) {
            return fraction(new BigInteger(decimal.group(4)), BigInteger.ONE);
        }
        return decimal(decimal.group(1), decimal.group(2), decimal.group(3));
    }

    /** 12,34(56): the whole part, the digits that do not repeat, and the ones that do. */
    private static Optional<NumericAnswer> decimal(String whole, String digits, String period) {
        if (whole.isEmpty() && digits.isEmpty() && period == null) {
            return Optional.empty();
        }
        BigInteger shift = BigInteger.TEN.pow(digits.length());
        BigInteger fixed = new BigInteger((whole + digits).isEmpty() ? "0" : whole + digits);
        if (period == null) {
            return fraction(fixed, shift);
        }
        BigInteger nines = BigInteger.TEN.pow(period.length()).subtract(BigInteger.ONE);
        return fraction(fixed.multiply(nines).add(new BigInteger(period)), shift.multiply(nines));
    }

    private static Optional<NumericAnswer> fraction(BigInteger top, BigInteger bottom) {
        return bottom.signum() == 0 ? Optional.empty() : Optional.of(new NumericAnswer(top, bottom));
    }

    private NumericAnswer negated() {
        return new NumericAnswer(numerator.negate(), denominator);
    }

    /** One text per value: answers written differently are counted together. */
    public String key() {
        return numerator + "/" + denominator;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NumericAnswer value && numerator.equals(value.numerator)
                && denominator.equals(value.denominator);
    }

    @Override
    public int hashCode() {
        return 31 * numerator.hashCode() + denominator.hashCode();
    }
}
