package lab.lesson.order.service;

import jakarta.persistence.EntityManager;
import lab.lesson.order.domain.Order;
import lab.lesson.order.repository.OrderRepository;
import lab.lesson.order.web.dto.CreateOrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OrderOptimisticLockTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void concurrentReplaceThrowsOptimisticLockingFailure() {
        CreateOrderRequest create = new CreateOrderRequest(
                "Alice",
                List.of(new CreateOrderRequest.OrderLineRequest("SKU-1", 1, new BigDecimal("10.00")))
        );
        var created = orderService.create(create);

        Order first = orderRepository.findById(created.id()).orElseThrow();
        Order second = orderRepository.findById(created.id()).orElseThrow();

        entityManager.detach(second);

        first.setCustomerName("First update");
        orderRepository.saveAndFlush(first);

        second.setCustomerName("Second update");

        assertThatThrownBy(() -> orderRepository.saveAndFlush(second))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
