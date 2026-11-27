package ro.mathlms.recap;

/** No collection exercise with that id (404). */
public class RecapNotFoundException extends RuntimeException {
    public RecapNotFoundException(Long id) {
        super("Exercițiul " + id + " nu există în culegere.");
    }
}
