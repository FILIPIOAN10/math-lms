package ro.mathlms.ingest;

import ro.mathlms.quiz.QuizItemType;

import java.util.List;

/** What the barem screens of the quiz builder send and get. */
public final class BaremDtos {

    private BaremDtos() {
    }

    /** Whether "Barem din poze" and the AI's grade proposals are on, and the teacher's barem photos left today. */
    public record AiGradingSettingsDto(boolean enabled, int baremMaxPages, int baremDailyPageLimit,
                                       long baremPagesUsedToday) {
    }

    /** One reading; {@code proposal} once it is DONE, mapped onto the test's current questions. */
    public record BaremReadDto(Long id, AiImportStatus status, String error, BaremProposalDto proposal) {
    }

    /**
     * The barem the AI read, question by question as the test has them now. {@code unmatched}: numbers the barem has
     * and the test does not. {@code taken}: students already took the test, so a grilă's right option can no longer
     * change (its barem text still can).
     */
    public record BaremProposalDto(List<ItemProposal> items, List<String> unmatched, boolean taken) {
    }

    /**
     * One question: what is saved now ({@code current*}) next to what the AI read ({@code proposed*}). For a grilă the
     * letters and the option id to send back; for an open question its barem, or one per requirement.
     */
    public record ItemProposal(Long itemId, String label, QuizItemType type, String statement, int points,
                               String currentLetter, String proposedLetter, Long proposedOptionId,
                               String currentBarem, String proposedBarem, List<PartProposal> parts,
                               List<String> warnings) {
    }

    public record PartProposal(Long partId, String label, int points, String statement, String currentBarem,
                               String proposedBarem) {
    }
}
