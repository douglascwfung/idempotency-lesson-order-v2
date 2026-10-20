package lab.lesson.order.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import lab.lesson.idem.IdempotencyKey;
import lab.lesson.idem.IdempotencyKeyReuseException;
import lab.lesson.idem.IdempotencyService;
import lab.lesson.order.service.OrderService;
import lab.lesson.order.web.dto.CreateOrderRequest;
import lab.lesson.order.web.dto.OrderResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerIdempotencyTest {

    @Mock
    private OrderService orderService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderController orderController;

    @Test
    void createWithNewKeyClaimsAndCompletes() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("Alice", List.of(
                new CreateOrderRequest.OrderLineRequest("SKU-1", 1, new BigDecimal("10.00"))
        ));
        OrderResponse response = new OrderResponse(1L, "Alice", List.of());

        when(objectMapper.writeValueAsString(request)).thenReturn("{\"customerName\":\"Alice\"}");
        when(idempotencyService.claimOrReplay(eq("key-1"), eq("user-1"), eq("/orders"), any())).thenReturn(Optional.empty());
        when(orderService.create(request)).thenReturn(response);
        when(objectMapper.writeValueAsString(response)).thenReturn("{\"id\":1}");

        OrderResponse result = orderController.create("key-1", "user-1", request);

        assertThat(result).isEqualTo(response);
        verify(idempotencyService).complete(eq("key-1"), eq(201), any());
    }

    @Test
    void createWithCompletedKeyReplaysStoredResponse() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("Alice", List.of());
        OrderResponse stored = new OrderResponse(5L, "Alice", List.of());
        IdempotencyKey key = new IdempotencyKey();
        key.setResponseBody("{\"id\":5}");

        when(objectMapper.writeValueAsString(request)).thenReturn("{}");
        when(idempotencyService.claimOrReplay(eq("key-2"), eq("user-1"), eq("/orders"), any())).thenReturn(Optional.of(key));
        when(objectMapper.readValue(eq("{\"id\":5}"), eq(OrderResponse.class))).thenReturn(stored);

        OrderResponse result = orderController.create("key-2", "user-1", request);

        assertThat(result).isEqualTo(stored);
        verify(orderService, org.mockito.Mockito.never()).create(any());
    }

    @Test
    void createWithSameKeyAndDifferentContentThrows() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("Bob", List.of());

        when(objectMapper.writeValueAsString(request)).thenReturn("{\"customerName\":\"Bob\"}");
        when(idempotencyService.claimOrReplay(eq("key-3"), eq("user-1"), eq("/orders"), any()))
                .thenThrow(new IdempotencyKeyReuseException("key-3"));

        assertThatThrownBy(() -> orderController.create("key-3", "user-1", request))
                .isInstanceOf(IdempotencyKeyReuseException.class);
    }

    @Test
    void createWithInProgressKeyThrowsWithKey() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("Alice", List.of());

        when(objectMapper.writeValueAsString(request)).thenReturn("{}");
        when(idempotencyService.claimOrReplay(eq("key-4"), eq("user-1"), eq("/orders"), any()))
                .thenThrow(new lab.lesson.idem.IdempotencyInProgressException("key-4"));

        assertThatThrownBy(() -> orderController.create("key-4", "user-1", request))
                .isInstanceOf(lab.lesson.idem.IdempotencyInProgressException.class)
                .hasFieldOrPropertyWithValue("key", "key-4");
    }
}
