package lab.lesson.order.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record SaveOrderChangesRequest(
        Integer version,
        List<LineUpdate> updates,
        List<CreateOrderRequest.OrderLineRequest> additions
) {
    public SaveOrderChangesRequest {
        updates = updates == null ? List.of() : updates;
        additions = additions == null ? List.of() : additions;
    }

    public record LineUpdate(Long lineId, LineChange line) {}

    public record LineChange(String sku, Integer quantity, BigDecimal unitPrice, Integer version) {}
}
