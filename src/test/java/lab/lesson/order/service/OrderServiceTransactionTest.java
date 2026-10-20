package lab.lesson.order.service;

import lab.lesson.order.domain.Order;
import lab.lesson.order.repository.OrderRepository;
import lab.lesson.order.web.dto.CreateOrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderServiceTransactionTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @Transactional
    void createSavesOrderAndLinesInOneTransaction() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Alice",
                List.of(
                        new CreateOrderRequest.OrderLineRequest("SKU-1", 2, new BigDecimal("10.00")),
                        new CreateOrderRequest.OrderLineRequest("SKU-2", 1, new BigDecimal("25.50"))
                )
        );

        var response = orderService.create(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.customerName()).isEqualTo("Alice");
        assertThat(response.lines()).hasSize(2);

        Order saved = orderRepository.findById(response.id()).orElseThrow();
        assertThat(saved.getLines()).hasSize(2);
        assertThat(saved.getLines().get(0).getSku()).isEqualTo("SKU-1");
    }
}
