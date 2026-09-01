package co.sena.sicot.exception;

import co.sena.sicot.exception.AccesoDenegadoException;
import co.sena.sicot.ia.IaNoDisponibleException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException ex, WebRequest request) {
        return build(ex.getMessage(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ErrorResponse> accesoDenegado(AccesoDenegadoException ex, WebRequest request) {
        log.debug("Acceso denegado (sin filtrar existencia) en {}: {}", request.getDescription(false).replace("uri=", ""), ex.getMessage());
        return build("El recurso solicitado no existe o no tiene acceso a él.", HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> business(BusinessException ex, WebRequest request) {
        return build(ex.getMessage(), HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Credenciales incorrectas o cuenta inactiva: <b>401</b>, no 400.
     *
     * <p>Antes viajaban como {@link BusinessException} y salían con 400, que
     * significa «la petición está mal formada» — y no lo estaba. Un cliente no
     * podía distinguir por código de estado entre un cuerpo inválido, una clave
     * equivocada y un bloqueo por intentos: tenía que leer el texto del
     * mensaje, que es exactamente lo que los códigos de estado existen para
     * evitar.
     */
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorResponse> credencialesInvalidas(CredencialesInvalidasException ex,
                                                                WebRequest request) {
        return build(ex.getMessage(), HttpStatus.UNAUTHORIZED, request);
    }

    /**
     * Límite de frecuencia superado: <b>429</b> con {@code Retry-After}.
     *
     * <p>Cubre el bloqueo por intentos fallidos de login y el techo de uso del
     * Copiloto de IA. La cabecera dice en segundos cuánto esperar, para que un
     * cliente pueda reintentar de forma automática en vez de a ciegas.
     */
    @ExceptionHandler(DemasiadasSolicitudesException.class)
    public ResponseEntity<ErrorResponse> demasiadasSolicitudes(DemasiadasSolicitudesException ex,
                                                                WebRequest request) {
        ErrorResponse cuerpo = ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                ex.getMessage(),
                request.getDescription(false).replace("uri=", ""),
                null);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getSegundosDeEspera()))
                .body(cuerpo);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accessDenied(AccessDeniedException ex, WebRequest request) {
        log.warn("Acceso denegado en {}: {}", request.getDescription(false).replace("uri=", ""), ex.getMessage());
        return build("No tiene permisos para realizar esta operación.", HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return build("Error de validación en los datos enviados.", HttpStatus.BAD_REQUEST, request, fieldErrors);
    }

    /**
     * Restricciones que fallan sobre un {@code @RequestParam} o un
     * {@code @PathVariable}.
     *
     * <p>Solo llega aquí cuando el controlador NO declara {@code @Validated} a
     * nivel de clase: en ese caso Spring MVC valida el método por su cuenta y
     * lanza esta excepción en vez de la {@link ConstraintViolationException}
     * que produce la vía AOP. Sin este manejador caía en el catch-all y salía
     * como <b>500</b>, convirtiendo un error del usuario en un fallo del
     * servidor. Se responde igual que la validación del cuerpo: 400 con el
     * mismo mapa {@code fieldErrors}, para que el frontend no tenga que
     * distinguir de dónde vino el fallo.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> validacionDeParametros(HandlerMethodValidationException ex,
                                                                 WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ParameterValidationResult resultado : ex.getAllValidationResults()) {
            String nombre = resultado.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : resultado.getResolvableErrors()) {
                fieldErrors.putIfAbsent(nombre != null ? nombre : "parametro", error.getDefaultMessage());
            }
        }
        return build("Error de validación en los datos enviados.", HttpStatus.BAD_REQUEST, request, fieldErrors);
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> badRequest(Exception ex, WebRequest request) {
        return build("Solicitud inválida. Revise los parámetros o el cuerpo de la petición.",
                HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> archivoDemasiadoGrande(MaxUploadSizeExceededException ex, WebRequest request) {
        return build("El archivo supera el tamaño máximo permitido.", HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> parteFaltante(MissingServletRequestPartException ex, WebRequest request) {
        return build("Falta un campo obligatorio en la solicitud: " + ex.getRequestPartName() + ".",
                HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> methodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                            WebRequest request) {
        return build("Método HTTP no soportado para esta ruta: " + ex.getMethod(),
                HttpStatus.METHOD_NOT_ALLOWED, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> dataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        return build("Los datos enviados violan una restricción de integridad (posible duplicado).",
                HttpStatus.CONFLICT, request);
    }

    /**
     * Dos transacciones escribieron sobre la misma fila y la segunda llegó con
     * una versión ya vencida.
     *
     * <p>Es la contraparte imprescindible del bloqueo optimista que introdujo
     * {@code V12__bloqueo_optimista.sql}. Antes de él, este caso no producía
     * ningún error: la segunda escritura simplemente pisaba a la primera y el
     * dato de la primera desaparecía sin dejar rastro. Ahora falla, y este
     * manejador convierte ese fallo en algo que el usuario puede resolver.
     *
     * <p>409 y no 500 porque no es un defecto del backend sino una colisión
     * legítima entre dos usuarios, y la operación se puede repetir con éxito
     * después de recargar. El mensaje dice explícitamente qué hacer: sin eso,
     * un "conflicto" a secas no le indica a nadie que debe volver a abrir el
     * contrato antes de reintentar.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> conflictoDeConcurrencia(OptimisticLockingFailureException ex,
                                                                  WebRequest request) {
        log.warn("Conflicto de edición concurrente en {}: {}",
                request.getDescription(false).replace("uri=", ""), ex.getMessage());
        return build("Otra persona modificó este registro mientras usted lo editaba. "
                        + "Recargue la información y vuelva a aplicar sus cambios.",
                HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> tipoDeContenidoNoSoportado(HttpMediaTypeNotSupportedException ex,
                                                                    WebRequest request) {
        return build("Tipo de contenido no soportado para esta ruta: " + ex.getContentType() + ".",
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, request);
    }

    /**
     * Ruta inexistente. Sin este handler, el catch-all de abajo las convertía en
     * 500 y escribía un stack trace completo por cada petición a una URL que no
     * existe — cualquier bot escaneando /wp-login.php llenaba el log de errores.
     * Se registra a nivel debug: una ruta equivocada no es un fallo del servidor.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> rutaNoEncontrada(Exception ex, WebRequest request) {
        log.debug("Ruta no encontrada: {}", request.getDescription(false).replace("uri=", ""));
        return build("La ruta solicitada no existe.", HttpStatus.NOT_FOUND, request);
    }

    /**
     * El servicio de IA local (Ollama) no está disponible.
     *
     * Este handler es la contraparte imprescindible del diseño de OllamaClient:
     * ese cliente falla con una excepción explícita en vez de inventar una
     * respuesta cuando Ollama no responde. Sin este handler el mensaje honesto
     * se perdía en el catch-all y el usuario recibía "Ocurrió un error interno
     * del servidor" — es decir, el sistema dejaba de explicar qué pasó, que es
     * justo lo que ese diseño trataba de evitar.
     *
     * 503 y no 500 porque es una dependencia externa caída, no un defecto del
     * backend: la operación puede volver a intentarse cuando Ollama esté arriba.
     * El mensaje de la excepción sí se propaga al cliente (a diferencia del
     * catch-all) porque lo redacta el propio backend y es accionable.
     */
    @ExceptionHandler(IaNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> iaNoDisponible(IaNoDisponibleException ex, WebRequest request) {
        log.warn("Servicio de IA no disponible en {}: {}",
                request.getDescription(false).replace("uri=", ""), ex.getMessage());
        return build(ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    /**
     * Fallo de entrada/salida al leer o generar un archivo (PDFBox al extraer
     * texto, al escribir un PDF, o al leer los bytes de una subida). No es un
     * error de programación sino una operación de E/S que falló, así que se
     * distingue del 500 genérico y se le da al usuario algo accionable.
     */
    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<ErrorResponse> errorDeEntradaSalida(UncheckedIOException ex, WebRequest request) {
        log.error("Fallo de E/S en {}", request.getDescription(false), ex);
        return build("No se pudo procesar el archivo. Verifique que no esté dañado e intente de nuevo.",
                HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> generic(Exception ex, WebRequest request) {
        // No se expone el detalle al cliente (evita filtrar stack traces),
        // pero sí queda en el log del servidor para poder diagnosticar.
        log.error("Error interno no controlado en {}", request.getDescription(false), ex);
        return build("Ocurrió un error interno del servidor.", HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<ErrorResponse> build(String message, HttpStatus status, WebRequest request) {
        return build(message, status, request, null);
    }

    private ResponseEntity<ErrorResponse> build(String message, HttpStatus status, WebRequest request,
                                                Map<String, String> fieldErrors) {
        ErrorResponse body = ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getDescription(false).replace("uri=", ""),
                fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
