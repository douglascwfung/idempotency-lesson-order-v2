package lab.lesson.idem;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;

    public IdempotencyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    /**
     * Key method for one idempotent write. This method owns the transaction and the
     * key lifecycle. Callers supply only the domain work as a lambda ({@code work}).
     *
     * <p>Inserts the key already as {@link IdempotencyKeyStatus#COMPLETED}, runs
     * {@code work} inside the open transaction, and stores the response body. There
     * is no IN_PROGRESS status. A crash rolls the key and the domain rows back
     * together. A concurrent insert of the same key hits the primary key; the service
     * reloads and either replays the completed response, replays a stored
     * {@link PermanentRejection} as {@link IdempotencyKeyStatus#FAILED}, or rejects a
     * hash mismatch.
     *
     * <p>Lesson 13: catch only {@link PermanentRejection} from {@code work}. Store
     * {@link IdempotencyKeyStatus#FAILED} with the rejection status and body, then
     * rethrow so the controller advice can return that response. Marked
     * {@code noRollbackFor = PermanentRejection.class} so the FAILED key row commits
     * while no business rows were written (PermanentRejection is thrown before writes).
     * Do not catch optimistic-lock or other exceptions — those must leave this method
     * so the transaction rolls back.
     *
     * <p>Lesson 11 wired {@code OrderService} directly inside create/add-line
     * overloads. Lesson 12 replaces that with a work callback. Controllers pass
     * {@code () -> orderService.create(...)} or {@code () -> orderService.saveChanges(...)}.
     */
    @Transactional(noRollbackFor = PermanentRejection.class)
    public <T> T runOrReplay(String key,
                             String userId,
                             String path,
                             String rawBody,
                             int responseStatus,
                             ThrowingSupplier<T> work,
                             ThrowingFunction<T, String> serializer,
                             ThrowingFunction<String, T> deserializer) throws Exception {
        String hash = hash(userId, path, rawBody);
        Optional<IdempotencyKey> existing = repository.findById(key);

        if (existing.isPresent()) {
            return replayOrReject(existing.get(), key, hash, deserializer);
        }

        IdempotencyKey created = new IdempotencyKey();
        created.setKeyValue(key);
        created.setRequestHash(hash);
        created.setStatus(IdempotencyKeyStatus.COMPLETED);

        try {
            repository.saveAndFlush(created);
        } catch (DataIntegrityViolationException e) {
            IdempotencyKey raced = repository.findById(key).orElseThrow();
            return replayOrReject(raced, key, hash, deserializer);
        }

        try {
            // Domain work runs inside this method's transaction (the box).
            T result = work.get();
            created.setResponseStatus(responseStatus);
            created.setResponseBody(serializer.apply(result));
            repository.save(created);
            return result;
        } catch (PermanentRejection ex) {
            // Early rejection before business writes. Store FAILED and rethrow.
            // noRollbackFor keeps this key row; nothing else was written.
            created.setStatus(IdempotencyKeyStatus.FAILED);
            created.setResponseStatus(ex.httpStatus());
            created.setResponseBody(ex.body());
            repository.save(created);
            throw ex;
        }
    }

    private <T> T replayOrReject(IdempotencyKey found,
                                 String key,
                                 String hash,
                                 ThrowingFunction<String, T> deserializer) throws Exception {
        if (!found.getRequestHash().equals(hash)) {
            throw new IdempotencyKeyReuseException(key);
        }
        if (found.getStatus() == IdempotencyKeyStatus.FAILED
                || (found.getResponseStatus() != null && found.getResponseStatus() >= 400)) {
            throw new PermanentRejection(found.getResponseStatus(), found.getResponseBody());
        }
        return deserializer.apply(found.getResponseBody());
    }

    private String hash(String userId, String path, String rawBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = userId + path + rawBody;
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Work callback: the domain action passed into {@link #runOrReplay} as a lambda.
     * Invoked only after the key is reserved, and only inside that method's transaction.
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingFunction<T, R> {
        R apply(T value) throws Exception;
    }
}
