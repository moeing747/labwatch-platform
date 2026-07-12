package com.labwatch.telemetry.api;

import com.labwatch.telemetry.application.InvalidTelemetryException;
import com.labwatch.telemetry.messaging.EventPublishException;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidTelemetryException.class)
    public ProblemDetail handleInvalidTelemetry(InvalidTelemetryException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleRequestValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        error -> error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage(),
                        (first, second) -> first));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Request body is invalid");
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(EventPublishException.class)
    public ProblemDetail handlePublishFailure(EventPublishException exception) {
        log.error("Telemetry publish failed", exception);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Event publishing failed",
                "Telemetry could not be published; retry later");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
