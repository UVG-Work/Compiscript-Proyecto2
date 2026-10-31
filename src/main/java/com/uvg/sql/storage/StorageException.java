package com.uvg.sql.storage;

/** Un archivo de la base no se pudo leer o escribir, o su contenido esta corrupto. */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
