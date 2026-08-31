package ro.mathlms.auth;

import jakarta.validation.constraints.NotNull;

/** The parent account to attach to the student named in the path. */
public record LinkParentRequest(
        @NotNull Long parentId
) {
}
