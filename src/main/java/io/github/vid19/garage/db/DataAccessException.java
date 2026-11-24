package io.github.vid19.garage.db;

/** Unchecked wrapper for SQL failures, so callers are not forced to handle SQLException everywhere. */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }
}
