package ro.mathlms.storage;

/** A rejected upload: its bytes are not a supported, bounded raster image. */
public class InvalidImageUploadException extends RuntimeException {
    public InvalidImageUploadException(String message) {
        super(message);
    }

    public InvalidImageUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
