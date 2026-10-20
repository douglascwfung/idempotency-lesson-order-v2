package lab.lesson.order.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        String customerName,
        List<OrderLineRequest> lines
) {
    public record OrderLineRequest(
            String sku,
            Integer quantity,
            BigDecimal unitPrice
    ) {}
}
