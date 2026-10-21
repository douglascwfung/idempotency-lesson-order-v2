package lab.lesson.idem;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyKey {

    @Id
    @Column(name = "key_value", nullable = false)
    private String keyValue;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    /**
     * After Lesson 11 this is always {@link IdempotencyKeyStatus#COMPLETED}.
     * Earlier teaching tags used {@code IN_PROGRESS} then {@code COMPLETED};
     * those paths are historical and not present from this tip onward.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdempotencyKeyStatus status;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
