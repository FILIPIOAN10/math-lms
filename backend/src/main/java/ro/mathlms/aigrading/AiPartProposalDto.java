package ro.mathlms.aigrading;

/** The AI's proposed points for one requirement a), b)... of an answer, with its short reason for the teacher. */
public record AiPartProposalDto(Long partId, String label, int points, String reason) {
}
