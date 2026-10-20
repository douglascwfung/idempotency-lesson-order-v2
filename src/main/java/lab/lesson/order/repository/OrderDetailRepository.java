package lab.lesson.order.repository;

import lab.lesson.order.domain.OrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {

    Optional<OrderDetail> findByIdAndOrderId(Long id, Long orderId);
}
