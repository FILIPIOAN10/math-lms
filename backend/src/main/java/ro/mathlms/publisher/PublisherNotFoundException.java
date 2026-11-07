package ro.mathlms.publisher;

public class PublisherNotFoundException extends RuntimeException {
    public PublisherNotFoundException(Long id) {
        super("Editura nu mai există (id " + id + ").");
    }
}
