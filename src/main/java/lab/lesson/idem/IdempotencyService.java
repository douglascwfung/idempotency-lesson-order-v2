package lab.lesson.idem;

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

    @Transactional
    public Optional<IdempotencyKey> claimOrReplay(String key, String userId, String path, String rawBody) {
        String hash = hash(userId, path, rawBody);
        Optional<IdempotencyKey> existing = repository.findById(key);

        if (existing.isPresent()) {
            IdempotencyKey found = existing.get();
            if (!found.getRequestHash().equals(hash)) {
                throw new IdempotencyKeyReuseException(key);
            }
            if ("IN_PROGRESS".equals(found.getStatus())) {
                throw new IdempotencyInProgressException(key);
            }
            return Optional.of(found);
        }

        IdempotencyKey claim = new IdempotencyKey();
        claim.setKeyValue(key);
        claim.setRequestHash(hash);
        claim.setStatus("IN_PROGRESS");
        repository.save(claim);
        return Optional.empty();
    }

    @Transactional
    public void complete(String key, int status, String responseBody) {
        IdempotencyKey found = repository.findById(key).orElseThrow();
        found.setStatus("COMPLETED");
        found.setResponseStatus(status);
        found.setResponseBody(responseBody);
        repository.save(found);
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
