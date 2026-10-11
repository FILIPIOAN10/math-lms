package ro.mathlms.review;

/** A review answer that does not fit today's queue; the message is shown as is (Romanian). */
public class InvalidReviewException extends RuntimeException {

    public InvalidReviewException(String message) {
        super(message);
    }
}
