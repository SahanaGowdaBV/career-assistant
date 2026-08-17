package career.assistant.api;

import career.assistant.document.customization.UntruthfulCustomizationException;
import career.assistant.document.service.ResumeConflictException;
import career.assistant.document.storage.ResumeStorageException;
import career.assistant.document.validation.ResumeValidationException;
import career.assistant.job.exception.DuplicateJobException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        exception.getBindingResult().getGlobalErrors().forEach(error ->
                errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage())
        );
        return response(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request,
                errors
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleMalformedId(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "Invalid value for '" + exception.getName() + "'",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, "Malformed request body", request, Map.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(DuplicateJobException.class)
    public ResponseEntity<ApiError> handleDuplicateJob(
            DuplicateJobException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDatabaseConstraint(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                "The request conflicts with a database constraint",
                request,
                Map.of()
        );
    }

    @ExceptionHandler({ResumeValidationException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ApiError> handleResumeValidation(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        String message = exception instanceof MaxUploadSizeExceededException
                ? "Resume exceeds the configured maximum upload size"
                : exception.getMessage();
        return response(HttpStatus.BAD_REQUEST, message, request, Map.of());
    }

    @ExceptionHandler(ResumeConflictException.class)
    public ResponseEntity<ApiError> handleResumeConflict(
            ResumeConflictException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(UntruthfulCustomizationException.class)
    public ResponseEntity<ApiError> handleUntruthfulCustomization(
            UntruthfulCustomizationException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ResumeStorageException.class)
    public ResponseEntity<ApiError> handleResumeStorage(
            ResumeStorageException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_GATEWAY, exception.getMessage(), request, Map.of());
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors
    ) {
        ApiError error = new ApiError(
                OffsetDateTime.now(ZoneOffset.UTC),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                validationErrors
        );
        return ResponseEntity.status(status).body(error);
    }
}
