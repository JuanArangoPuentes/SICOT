package co.sena.sicot.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String recurso, Long id) {
        return new ResourceNotFoundException(recurso + " con id " + id + " no fue encontrado(a).");
    }
}
