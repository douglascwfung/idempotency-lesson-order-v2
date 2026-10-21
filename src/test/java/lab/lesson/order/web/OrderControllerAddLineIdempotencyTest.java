package lab.lesson.order.web;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerAddLineIdempotencyTest {

    @Mock
    private OrderService orderService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderController orderController;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void setUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-1", null, List.of()));
    }

    @Test
    void addLineWithNewKeyRunOrReplayCompletes() throws Exception {
        CreateOrderRequest.OrderLineRequest request =
                new CreateOrderRequest.OrderLineRequest("SKU-1", 1, new BigDecimal("10.00"));
        OrderResponse.OrderLineResponse response =
                new OrderResponse.OrderLineResponse(10L, "SKU-1", 1, new BigDecimal("10.00"), 0);

        setUser();
        when(objectMapper.writeValueAsString(request)).thenReturn("{\"sku\":\"SKU-1\"}");
        when(idempotencyService.runOrReplay(eq("key-1"), eq("user-1"), eq("/orders/1/lines"), any(), eq(201), any(), any(), any()))
                .thenAnswer(invocation -> {
                    IdempotencyService.ThrowingSupplier<OrderResponse.OrderLineResponse> work = invocation.getArgument(5);
                    return work.get();
                });
        when(orderService.addLine(1L, request)).thenReturn(response);

        OrderResponse.OrderLineResponse result = orderController.addLine(1L, "key-1", request);

        assertThat(result).isEqualTo(response);
        verify(idempotencyService).runOrReplay(eq("key-1"), eq("user-1"), eq("/orders/1/lines"), any(), eq(201), any(), any(), any());
        verify(orderService).addLine(1L, request);
    }

    @Test
    void addLineWithCompletedKeyReplays() throws Exception {
        CreateOrderRequest.OrderLineRequest request =
                new CreateOrderRequest.OrderLineRequest("SKU-1", 1, new BigDecimal("10.00"));
        OrderResponse.OrderLineResponse stored =
                new OrderResponse.OrderLineResponse(10L, "SKU-1", 1, new BigDecimal("10.00"), 0);

        setUser();
        when(objectMapper.writeValueAsString(request)).thenReturn("{}");
        when(idempotencyService.runOrReplay(eq("key-2"), eq("user-1"), eq("/orders/1/lines"), any(), eq(201), any(), any(), any()))
                .thenReturn(stored);

        OrderResponse.OrderLineResponse result = orderController.addLine(1L, "key-2", request);

        assertThat(result).isEqualTo(stored);
        verify(orderService, org.mockito.Mockito.never()).addLine(any(), any());
    }
}
