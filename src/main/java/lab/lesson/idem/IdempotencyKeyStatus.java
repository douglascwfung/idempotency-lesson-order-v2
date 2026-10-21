package lab.lesson.idem;

/**
 * Allowed values for {@link IdempotencyKey#getStatus()}.
 *
 * <p>Lesson 11 removed {@code IN_PROGRESS}. Keys for successful work are inserted
 * already as {@link #COMPLETED} inside the same transaction as the Order write.
 * Lesson 13 adds {@link #FAILED} for permanent early rejections only
 * ({@link PermanentRejection} thrown before any business row changes). There is
 * still no {@code IN_PROGRESS}. Flyway {@code V5} introduced COMPLETED-only;
 * {@code V6} widens the CHECK to {@code COMPLETED} or {@code FAILED}.
 */
public enum IdempotencyKeyStatus {
    COMPLETED,
    FAILED
}
