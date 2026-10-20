package lab.lesson.order.service;

import lab.lesson.order.domain.Order;
import lab.lesson.order.domain.OrderDetail;
import lab.lesson.order.repository.OrderDetailRepository;
import lab.lesson.order.repository.OrderRepository;
import lab.lesson.order.web.dto.CreateOrderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderLineServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderDetailRepository orderDetailRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void updateLineRejectsStaleVersion() {
        Order order = new Order();
        order.setId(1L);

        OrderDetail detail = new OrderDetail();
        detail.setId(10L);
        detail.setVersion(1);
        detail.setSku("SKU-1");
        detail.setQuantity(2);
        detail.setUnitPrice(new BigDecimal("10.00"));
        detail.setOrder(order);

        when(orderDetailRepository.findByIdAndOrderId(10L, 1L)).thenReturn(Optional.of(detail));

        CreateOrderRequest.OrderLineRequest request =
                new CreateOrderRequest.OrderLineRequest("SKU-1", 3, new BigDecimal("10.00"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> orderService.updateLine(1L, 10L, 0, request))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    }
}
