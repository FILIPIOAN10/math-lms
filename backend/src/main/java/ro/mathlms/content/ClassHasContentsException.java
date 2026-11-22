package ro.mathlms.content;

/**
 * A plain delete of a class that still has books or tests of its own: refused (409), with how many of each a delete
 * {@code withContents} would take along, so the menu can ask a second, explicit time.
 */
public class ClassHasContentsException extends ContentInUseException {

    private final long books;
    private final long quizzes;

    public ClassHasContentsException(String message, long books, long quizzes) {
        super(message);
        this.books = books;
        this.quizzes = quizzes;
    }

    public long books() {
        return books;
    }

    public long quizzes() {
        return quizzes;
    }
}
