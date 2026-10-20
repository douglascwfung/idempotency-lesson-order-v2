package lab.lesson.idem;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class IdempotencyInProgressException extends RuntimeException {

    private final String key;

    public IdempotencyInProgressException(String key) {
        super("Request with key " + key + " is still in progress");
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
