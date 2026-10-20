package lab.lesson.order.web;

import lab.lesson.order.service.OrderService;
import lab.lesson.order.web.dto.CreateOrderRequest;
import lab.lesson.order.web.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    @Test
    void createDelegatesToServiceAndReturnsCreated() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Alice",
                List.of(new CreateOrderRequest.OrderLineRequest("SKU-1", 2, new BigDecimal("10.00")))
        );
        OrderResponse response = new OrderResponse(1L, "Alice", List.of(
                new OrderResponse.OrderLineResponse(10L, "SKU-1", 2, new BigDecimal("10.00"))
        ));
        when(orderService.create(request)).thenReturn(response);

        OrderResponse result = orderController.create(request);

        assertThat(result).isEqualTo(response);
        verify(orderService).create(request);
    }

    @Test
    void getDelegatesToService() {
        OrderResponse response = new OrderResponse(1L, "Alice", List.of());
        when(orderService.get(1L)).thenReturn(response);

        OrderResponse result = orderController.get(1L);

        assertThat(result).isEqualTo(response);
        verify(orderService).get(1L);
    }

    @Test
    void replaceDelegatesToService() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Bob",
                List.of(new CreateOrderRequest.OrderLineRequest("SKU-2", 1, new BigDecimal("20.00")))
        );
        OrderResponse response = new OrderResponse(1L, "Bob", List.of(
                new OrderResponse.OrderLineResponse(11L, "SKU-2", 1, new BigDecimal("20.00"))
        ));
        when(orderService.replace(eq(1L), any(CreateOrderRequest.class))).thenReturn(response);

        OrderResponse result = orderController.replace(1L, request);

        assertThat(result).isEqualTo(response);
        verify(orderService).replace(1L, request);
    }
}
