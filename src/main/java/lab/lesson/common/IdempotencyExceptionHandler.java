package lab.lesson.common;

import lab.lesson.idem.IdempotencyConflictResponse;
import lab.lesson.idem.IdempotencyKeyReuseException;
import lab.lesson.idem.PermanentRejection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    @ExceptionHandler(PermanentRejection.class)
    public ResponseEntity<String> handlePermanentRejection(PermanentRejection ex) {
        return ResponseEntity.status(ex.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ex.body());
    }
}
