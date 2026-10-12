package ro.mathlms.grading;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What the saved-comments endpoints send and take. */
public final class CommentSnippetDtos {

    private CommentSnippetDtos() {
    }

    public record SnippetDto(Long id, String text, int useCount) {
        static SnippetDto from(CommentSnippet snippet) {
            return new SnippetDto(snippet.getId(), snippet.getText(), snippet.getUseCount());
        }
    }

    public record CreateSnippetRequest(@NotBlank @Size(max = CommentSnippet.MAX_TEXT) String text) {
    }
}
