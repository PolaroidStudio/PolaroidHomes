package me.juancayc.polaroidhomes.storage;

/**
 * Wraps every {@code SQLException} that crosses out of the storage package.
 *
 * <p>The service and GUI layers must not import {@code java.sql}: leaking JDBC types there would
 * make the backend visible to code whose whole point is not knowing which backend is in use.
 */
public final class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
