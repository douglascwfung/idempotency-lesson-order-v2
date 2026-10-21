package lab.lesson.order.service;

import lab.lesson.idem.PermanentRejection;
import lab.lesson.order.domain.Order;
import lab.lesson.order.domain.OrderDetail;
import lab.lesson.order.repository.OrderDetailRepository;
import lab.lesson.order.repository.OrderRepository;
import lab.lesson.order.web.dto.SaveOrderChangesRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderDetailRepository orderDetailRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void saveChangesRejectsAStaleLineBeforeWriting() {
        Order order = new Order();
        order.setId(1L);
        order.setCustomerName("Ada");
        order.setVersion(0);

        OrderDetail line = new OrderDetail();
        line.setId(7L);
        line.setSku("widget");
        line.setQuantity(2);
        line.setUnitPrice(new BigDecimal("9.50"));
        line.setVersion(3);
        line.setOrder(order);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderDetailRepository.findByIdAndOrderId(7L, 1L)).thenReturn(Optional.of(line));

        SaveOrderChangesRequest request = new SaveOrderChangesRequest(
                0,
                List.of(new SaveOrderChangesRequest.LineUpdate(
                        7L,
                        new SaveOrderChangesRequest.LineChange("widget", 4, new BigDecimal("9.50"), 0))),
                List.of());

        assertThatThrownBy(() -> orderService.saveChanges(1L, request))
                .isInstanceOf(PermanentRejection.class)
                .satisfies(ex -> {
                    PermanentRejection rejection = (PermanentRejection) ex;
                    assertThat(rejection.httpStatus()).isEqualTo(409);
                    assertThat(rejection.body()).contains("order line version conflict");
                });

        verify(orderDetailRepository, never()).save(any());
        assertThat(line.getQuantity()).isEqualTo(2);
    }
}
