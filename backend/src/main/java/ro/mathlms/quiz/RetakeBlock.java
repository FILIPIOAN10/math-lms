package ro.mathlms.quiz;

/** Why a student may not start another graded attempt at a quiz. */
public enum RetakeBlock {
    NO_ATTEMPTS_LEFT("Ai folosit toate încercările la acest test."),
    WAITING_FOR_GRADE("Lucrarea ta se corectează. Poți da testul din nou după ce primești nota.");

    private final String message;

    RetakeBlock(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
