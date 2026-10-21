package lab.lesson.order.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import lab.lesson.idem.IdempotencyKeyReuseException;
import lab.lesson.idem.IdempotencyService;
import lab.lesson.order.service.OrderService;
import lab.lesson.order.web.dto.CreateOrderRequest;
import lab.lesson.order.web.dto.OrderResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

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

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void setUser(String userId) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void createWithNewKeyRunOrReplayCompletes() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("Alice", List.of(
                new CreateOrderRequest.OrderLineRequest("SKU-1", 1, new BigDecimal("10.00"))
        ));
        OrderResponse response = new OrderResponse(1L, "Alice", List.of());

        when(objectMapper.writeValueAsString(request)).thenReturn("{\"customerName\":\"Alice\"}");
        setUser("user-1");
        when(idempotencyService.runOrReplay(eq("key-1"), eq("user-1"), eq("/orders"), any(), eq(201), any(), any(), any()))
                .thenAnswer(invocation -> {
                    IdempotencyService.ThrowingSupplier<OrderResponse> work = invocation.getArgument(5);
                    return work.get();
                });
        when(orderService.create(request)).thenReturn(response);

        OrderResponse result = orderController.create("key-1", request);

        assertThat(result).isEqualTo(response);
        verify(idempotencyService).runOrReplay(eq("key-1"), eq("user-1"), eq("/orders"), any(), eq(201), any(), any(), any());
        verify(orderService).create(request);
    }

    @Test
    void createWithCompletedKeyReplaysStoredResponse() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("Alice", List.of());
        OrderResponse stored = new OrderResponse(5L, "Alice", List.of());

        when(objectMapper.writeValueAsString(request)).thenReturn("{}");
        setUser("user-1");
        when(idempotencyService.runOrReplay(eq("key-2"), eq("user-1"), eq("/orders"), any(), eq(201), any(), any(), any()))
                .thenReturn(stored);

        OrderResponse result = orderController.create("key-2", request);

        assertThat(result).isEqualTo(stored);
        verify(orderService, org.mockito.Mockito.never()).create(any());
    }

    @Test
    void createWithSameKeyAndDifferentContentThrows() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("Bob", List.of());

        when(objectMapper.writeValueAsString(request)).thenReturn("{\"customerName\":\"Bob\"}");
        setUser("user-1");
        when(idempotencyService.runOrReplay(eq("key-3"), eq("user-1"), eq("/orders"), any(), eq(201), any(), any(), any()))
                .thenThrow(new IdempotencyKeyReuseException("key-3"));

        assertThatThrownBy(() -> orderController.create("key-3", request))
                .isInstanceOf(IdempotencyKeyReuseException.class);
    }
}
