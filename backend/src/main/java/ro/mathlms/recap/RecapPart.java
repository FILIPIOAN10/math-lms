package ro.mathlms.recap;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One requirement a), b)... of a collection exercise: its text, the AI barem, the answer in words and - only when both AI
 * runs reached the same plain number - the result a student types ({@code result}); {@code verified} = the two runs
 * agreed on it. The label follows from its place in the list.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecapPart {

    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    @Column(columnDefinition = "TEXT")
    private String solution;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(length = 40)
    private String result;

    @Column(nullable = false)
    private boolean verified;

    public RecapPart(String statement, String solution, String answer, String result, boolean verified) {
        this.statement = statement;
        this.solution = solution;
        this.answer = answer;
        this.result = result;
        this.verified = verified;
    }

    /** "a", "b", "c"... */
    public static String label(int position) {
        return String.valueOf((char) ('a' + position));
    }
}
