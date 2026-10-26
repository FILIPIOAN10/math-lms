package ro.mathlms.aigrading;

import java.util.List;

/**
 * The AI's proposal for one open answer, as the grading screen shows it next to the photo: while QUEUED/RUNNING only
 * the status; once DONE the points (in total and per requirement), the reason and how sure it is; FAILED says why.
 */
public record AiProposalDto(Long itemId, AiProposalStatus status, Integer points, List<AiPartProposalDto> parts,
                            String reason, AiConfidence confidence, String error) {
}
