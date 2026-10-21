package lab.lesson.common;

import lab.lesson.idem.IdempotencyConflictResponse;
import lab.lesson.idem.IdempotencyKeyReuseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IdempotencyExceptionHandler {

    @ExceptionHandler(IdempotencyKeyReuseException.class)
    public ResponseEntity<IdempotencyConflictResponse> handleReuse(IdempotencyKeyReuseException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new IdempotencyConflictResponse(null, ex.getMessage()));
    }
}
