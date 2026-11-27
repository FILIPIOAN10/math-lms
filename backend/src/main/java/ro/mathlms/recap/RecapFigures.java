package ro.mathlms.recap;

import java.util.regex.Pattern;

/**
 * Whether an exercise's text points at a printed drawing: "În figura 1", "figura alăturată", "din figură", "(fig. 3)".
 * Not "figurează" or "metoda figurativă" (other words), and not the transcription's notes in HTML comments ("No figures
 * found"). The same reading as tutor/answer_check.mentions_figure and as migration V83.
 */
public final class RecapFigures {

    private static final Pattern NOTE = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern FIGURE = Pattern.compile("\\bfigur(a|ă|ii|ile|i)\\b|\\bfig\\.\\s*\\d",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    private RecapFigures() {
    }

    public static boolean mentionsFigure(String text) {
        return text != null && FIGURE.matcher(NOTE.matcher(text).replaceAll(" ")).find();
    }
}
