package io.github.vid19.garage.db;

/**
 * Thrown inside a transaction when a conditional UPDATE matched zero rows, meaning the row was not in the
 * state we expected (someone else got there first). The transaction is rolled back.
 */
public class StaleStateException extends DataAccessException {

    private static final long serialVersionUID = 1L;

    public StaleStateException(String message) {
        super(message);
    }
}
