package lab.lesson.order.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponse(
        Long id,
        String customerName,
        List<OrderLineResponse> lines
) {
    public record OrderLineResponse(
            Long id,
            String sku,
            Integer quantity,
            BigDecimal unitPrice,
            Integer version
    ) {}
}
