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
     * Lesson 11 removed {@code IN_PROGRESS}; successful keys are {@link IdempotencyKeyStatus#COMPLETED}.
     * Lesson 13 may set {@link IdempotencyKeyStatus#FAILED} when {@link PermanentRejection}
     * is thrown before any business row changes. Those paths are the only allowed statuses
     * from this tip onward.
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
