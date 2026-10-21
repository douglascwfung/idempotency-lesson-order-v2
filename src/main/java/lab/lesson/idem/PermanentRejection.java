package lab.lesson.idem;

/**
 * A request rejected before any business row is changed, such as a stale version
 * found by the manual check in {@code OrderService.saveChanges}.
 * {@link IdempotencyService#runOrReplay} may catch this, store {@link IdempotencyKeyStatus#FAILED},
 * and rethrow so the HTTP layer returns the same status and body.
 *
 * <p>Do not catch {@code OptimisticLockingFailureException} /
 * {@code ObjectOptimisticLockingFailureException} as PermanentRejection. Those can
 * happen after a line has already been changed and must leave {@code runOrReplay}
 * so the transaction rolls back.
 */
public class PermanentRejection extends RuntimeException {

    private final int httpStatus;
    private final String body;

    public PermanentRejection(int httpStatus, String body) {
        super(body);
        this.httpStatus = httpStatus;
        this.body = body;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String body() {
        return body;
    }
}
