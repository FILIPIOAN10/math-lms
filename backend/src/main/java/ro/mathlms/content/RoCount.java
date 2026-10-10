package ro.mathlms.content;

/**
 * "1 carte", "2 cărți", "20 de cărți": Romanian puts "de" between a number and its noun when
 * the number's last two digits are 00 or 20–99.
 */
public final class RoCount {

    private RoCount() {
    }

    public static String of(long n, String one, String many) {
        if (n == 1) {
            return "1 " + one;
        }
        long lastTwo = n % 100;
        boolean de = n != 0 && (lastTwo == 0 || lastTwo >= 20);
        return n + (de ? " de " : " ") + many;
    }
}
