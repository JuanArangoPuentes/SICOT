package co.sena.sicot.exception;

public class ResourceNotFoundException extends RuntimeException {

    /**
     * Si la excepción nació de buscar un registro por su identificador
     * ({@link #of}). Es el caso en que el mensaje revela si el id existe, y el
     * que {@code GlobalExceptionHandler} iguala para un supervisor con el de un
     * recurso ajeno. Los mensajes escritos a mano explican algo de un recurso
     * al que ya se comprobó el acceso, y se conservan.
     */
    private final boolean busquedaPorId;

    public ResourceNotFoundException(String message) {
        this(message, false);
    }

    private ResourceNotFoundException(String message, boolean busquedaPorId) {
        super(message);
        this.busquedaPorId = busquedaPorId;
    }

    public static ResourceNotFoundException of(String recurso, Long id) {
        return new ResourceNotFoundException(recurso + " con id " + id + " no fue encontrado(a).", true);
    }

    public boolean esBusquedaPorId() {
        return busquedaPorId;
    }
}
