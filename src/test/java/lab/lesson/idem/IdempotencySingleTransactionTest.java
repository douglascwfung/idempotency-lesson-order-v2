package lab.lesson.idem;

import com.fasterxml.jackson.databind.ObjectMapper;
import lab.lesson.order.repository.OrderRepository;
import lab.lesson.order.service.OrderService;
import lab.lesson.order.web.dto.CreateOrderRequest;
import lab.lesson.order.web.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IdempotencySingleTransactionTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private IdempotencyKeyRepository keyRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createWithKeyCommitsOrderAndCompletedKeyTogether() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "Alice",
                List.of(new CreateOrderRequest.OrderLineRequest("SKU-1", 1, new BigDecimal("10.00")))
        );
        String rawBody = objectMapper.writeValueAsString(request);

        OrderResponse response = idempotencyService.runOrReplay(
                "key-st-1",
                "user-1",
                "/orders",
                rawBody,
                201,
                () -> orderService.create(request),
                objectMapper::writeValueAsString,
                body -> objectMapper.readValue(body, OrderResponse.class));

        IdempotencyKey key = keyRepository.findById("key-st-1").orElseThrow();
        assertThat(key.getStatus()).isEqualTo(IdempotencyKeyStatus.COMPLETED);
        assertThat(key.getResponseStatus()).isEqualTo(201);
        assertThat(key.getResponseBody()).isNotBlank();
        assertThat(orderRepository.findById(response.id())).isPresent();
    }

    @Test
    void failedWorkRollsBackKeyAndOrder() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "Bob",
                List.of(new CreateOrderRequest.OrderLineRequest("SKU-2", 1, new BigDecimal("5.00")))
        );
        String rawBody = objectMapper.writeValueAsString(request);
        long ordersBefore = orderRepository.count();

        assertThatThrownBy(() -> idempotencyService.runOrReplay(
                "key-st-fail",
                "user-1",
                "/orders",
                rawBody,
                201,
                () -> {
                    throw new RuntimeException("simulated crash before Order write completes");
                },
                objectMapper::writeValueAsString,
                body -> objectMapper.readValue(body, OrderResponse.class)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated crash");

        assertThat(keyRepository.findById("key-st-fail")).isEmpty();
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    }

    @Test
    void failureUnderTheLambdaAfterOrderWriteRollsBackKeyAndOrder() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "Dana",
                List.of(new CreateOrderRequest.OrderLineRequest("SKU-4", 1, new BigDecimal("3.00")))
        );
        String rawBody = objectMapper.writeValueAsString(request);
        long ordersBefore = orderRepository.count();

        assertThatThrownBy(() -> idempotencyService.runOrReplay(
                "key-st-lambda-fail",
                "user-1",
                "/orders",
                rawBody,
                201,
                () -> {
                    orderService.create(request);
                    throw new RuntimeException("fail after Order write inside lambda");
                },
                objectMapper::writeValueAsString,
                body -> objectMapper.readValue(body, OrderResponse.class)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("fail after Order write");

        assertThat(keyRepository.findById("key-st-lambda-fail")).isEmpty();
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    }

    @Test
    void completedKeyReplaysWithoutSecondOrder() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "Carol",
                List.of(new CreateOrderRequest.OrderLineRequest("SKU-3", 2, new BigDecimal("8.00")))
        );
        String rawBody = objectMapper.writeValueAsString(request);

        OrderResponse first = idempotencyService.runOrReplay(
                "key-st-replay",
                "user-1",
                "/orders",
                rawBody,
                201,
                () -> orderService.create(request),
                objectMapper::writeValueAsString,
                body -> objectMapper.readValue(body, OrderResponse.class));

        long ordersAfterFirst = orderRepository.count();

        OrderResponse second = idempotencyService.runOrReplay(
                "key-st-replay",
                "user-1",
                "/orders",
                rawBody,
                201,
                () -> orderService.create(request),
                objectMapper::writeValueAsString,
                body -> objectMapper.readValue(body, OrderResponse.class));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(orderRepository.count()).isEqualTo(ordersAfterFirst);
    }
}
