package ro.mathlms.publisher;

public class PublisherNotFoundException extends RuntimeException {
    public PublisherNotFoundException(Long id) {
        this("Editura", id);
    }

    /** "Editura" / "Cartea" nu mai există. */
    public PublisherNotFoundException(String what, Long id) {
        super(what + " nu mai există (id " + id + ").");
    }
}
