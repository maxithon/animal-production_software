package rw.animalproduct.animal.production.exception;

/**
 * Thrown when a new/updated buyer would duplicate an existing phone,
 * National ID, or email. Carries the offending field name so the
 * controller can bind the error message directly under that field in
 * the form instead of showing a vague top-level flash message.
 */
public class DuplicateBuyerException extends RuntimeException {

    private final String field;

    public DuplicateBuyerException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
}
