package lab.lesson.idem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceRaceTest {

    @Mock
    private IdempotencyKeyRepository repository;

    @InjectMocks
    private IdempotencyService service;

    @Test
    void concurrentClaimOfInProgressKeyThrows() {
        IdempotencyKey raced = new IdempotencyKey();
        raced.setKeyValue("key-1");
        raced.setRequestHash(serviceHash());
        raced.setStatus("IN_PROGRESS");

        when(repository.findById("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raced));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.claimOrReplay("key-1", "user-1", "/orders", "{}"))
                .isInstanceOf(IdempotencyInProgressException.class);
    }

    private String serviceHash() {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest("user-1/orders{}".getBytes());
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
