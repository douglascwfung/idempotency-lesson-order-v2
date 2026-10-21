package lab.lesson.idem;

import com.fasterxml.jackson.databind.ObjectMapper;
import lab.lesson.order.service.OrderService;
import lab.lesson.order.web.dto.CreateOrderRequest;
import lab.lesson.order.web.dto.OrderResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Single-transaction idempotency for Order writes. Lesson 11 wires
 * {@link OrderService} directly inside {@code runOrReplay} — there is no work
 * lambda yet. Lesson 12 replaces that coupling with a {@code ThrowingSupplier}.
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository repository,
                              OrderService orderService,
                              ObjectMapper objectMapper) {
        this.repository = repository;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    /**
     * Inserts the key already as {@link IdempotencyKeyStatus#COMPLETED}, calls
     * {@link OrderService#create} directly, and stores the response in one
     * transaction. There is no {@code IN_PROGRESS} status and no work callback.
     * The enum and Flyway {@code V5} CHECK enforce COMPLETED-only. A crash rolls
     * the key and the Order back together. A concurrent insert of the same key
     * hits the primary key; the service reloads and either replays the completed
     * response or rejects a hash mismatch.
     */
    @Transactional
    public OrderResponse runOrReplay(String key,
                                     String userId,
                                     String path,
                                     String rawBody,
                                     int responseStatus,
                                     CreateOrderRequest request) throws Exception {
        String hash = hash(userId, path, rawBody);
        Optional<IdempotencyKey> existing = repository.findById(key);

        if (existing.isPresent()) {
            return replayOrReject(existing.get(), key, hash, OrderResponse.class);
        }

        IdempotencyKey created = new IdempotencyKey();
        created.setKeyValue(key);
        created.setRequestHash(hash);
        created.setStatus(IdempotencyKeyStatus.COMPLETED);

        try {
            repository.saveAndFlush(created);
        } catch (DataIntegrityViolationException e) {
            IdempotencyKey raced = repository.findById(key).orElseThrow();
            return replayOrReject(raced, key, hash, OrderResponse.class);
        }

        // Domain write runs inside this method's transaction (direct call, no lambda).
        OrderResponse result = orderService.create(request);
        created.setResponseStatus(responseStatus);
        created.setResponseBody(objectMapper.writeValueAsString(result));
        repository.save(created);
        return result;
    }

    /**
     * Same single-transaction COMPLETED-only path for add-line: inserts the key,
     * calls {@link OrderService#addLine} directly, and stores the response.
     */
    @Transactional
    public OrderResponse.OrderLineResponse runOrReplay(String key,
                                                       String userId,
                                                       String path,
                                                       String rawBody,
                                                       int responseStatus,
                                                       Long orderId,
                                                       CreateOrderRequest.OrderLineRequest request) throws Exception {
        String hash = hash(userId, path, rawBody);
        Optional<IdempotencyKey> existing = repository.findById(key);

        if (existing.isPresent()) {
            return replayOrReject(existing.get(), key, hash, OrderResponse.OrderLineResponse.class);
        }

        IdempotencyKey created = new IdempotencyKey();
        created.setKeyValue(key);
        created.setRequestHash(hash);
        created.setStatus(IdempotencyKeyStatus.COMPLETED);

        try {
            repository.saveAndFlush(created);
        } catch (DataIntegrityViolationException e) {
            IdempotencyKey raced = repository.findById(key).orElseThrow();
            return replayOrReject(raced, key, hash, OrderResponse.OrderLineResponse.class);
        }

        OrderResponse.OrderLineResponse result = orderService.addLine(orderId, request);
        created.setResponseStatus(responseStatus);
        created.setResponseBody(objectMapper.writeValueAsString(result));
        repository.save(created);
        return result;
    }

    private <T> T replayOrReject(IdempotencyKey found,
                                 String key,
                                 String hash,
                                 Class<T> type) throws Exception {
        if (!found.getRequestHash().equals(hash)) {
            throw new IdempotencyKeyReuseException(key);
        }
        return objectMapper.readValue(found.getResponseBody(), type);
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
}
