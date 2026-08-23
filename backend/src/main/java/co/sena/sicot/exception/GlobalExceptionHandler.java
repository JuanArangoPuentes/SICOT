package co.sena.sicot.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException ex, WebRequest request) {
        return build(ex.getMessage(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> business(BusinessException ex, WebRequest request) {
        return build(ex.getMessage(), HttpStatus.BAD_REQUEST, request);
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
