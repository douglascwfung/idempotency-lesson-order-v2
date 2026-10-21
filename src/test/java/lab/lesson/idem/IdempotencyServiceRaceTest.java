package lab.lesson.idem;

import com.fasterxml.jackson.databind.ObjectMapper;
import lab.lesson.order.service.OrderService;
import lab.lesson.order.web.dto.CreateOrderRequest;
import lab.lesson.order.web.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceRaceTest {

    @Mock
    private IdempotencyKeyRepository repository;

    @Mock
    private OrderService orderService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private IdempotencyService service;

    @Test
    void concurrentClaimOfSameKeyReplaysCompletedResponse() throws Exception {
        IdempotencyKey raced = new IdempotencyKey();
        raced.setKeyValue("key-1");
        raced.setRequestHash(serviceHash());
        raced.setStatus(IdempotencyKeyStatus.COMPLETED);
        raced.setResponseStatus(201);
        raced.setResponseBody("{\"id\":9,\"customerName\":\"Alice\",\"lines\":[]}");

        OrderResponse replayed = new OrderResponse(9L, "Alice", List.of());
        CreateOrderRequest request = new CreateOrderRequest("Alice", List.of());

        when(repository.findById("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raced));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(objectMapper.readValue(raced.getResponseBody(), OrderResponse.class)).thenReturn(replayed);

        OrderResponse result = service.runOrReplay(
                "key-1",
                "user-1",
                "/orders",
                "{}",
                201,
                request);

        assertThat(result).isEqualTo(replayed);
        verify(orderService, never()).create(any());
    }

    @Test
    void concurrentClaimWithDifferentHashThrowsReuse() {
        IdempotencyKey raced = new IdempotencyKey();
        raced.setKeyValue("key-1");
        raced.setRequestHash("different-hash");
        raced.setStatus(IdempotencyKeyStatus.COMPLETED);
        raced.setResponseBody("{}");

        CreateOrderRequest request = new CreateOrderRequest("Alice", List.of());

        when(repository.findById("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raced));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.runOrReplay(
                "key-1",
                "user-1",
                "/orders",
                "{}",
                201,
                request))
                .isInstanceOf(IdempotencyKeyReuseException.class);
        verify(orderService, never()).create(any());
    }

    private String serviceHash() {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest("user-1/orders{}".getBytes());
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
