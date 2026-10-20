package lab.lesson.order.service;

import lab.lesson.order.domain.Order;
import lab.lesson.order.domain.OrderDetail;
import lab.lesson.order.exception.OrderNotFoundException;
import lab.lesson.order.repository.OrderRepository;
import lab.lesson.order.web.dto.CreateOrderRequest;
import lab.lesson.order.web.dto.OrderResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public OrderResponse create(CreateOrderRequest request) {
        Order order = new Order();
        order.setCustomerName(request.customerName());
        request.lines().forEach(lineReq -> {
            OrderDetail detail = new OrderDetail();
            detail.setSku(lineReq.sku());
            detail.setQuantity(lineReq.quantity());
            detail.setUnitPrice(lineReq.unitPrice());
            order.addLine(detail);
        });
        Order saved = orderRepository.save(order);
        return toResponse(saved);
    }

    public OrderResponse get(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return toResponse(order);
    }

    public OrderResponse replace(Long id, CreateOrderRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        order.setCustomerName(request.customerName());
        order.clearLines();
        request.lines().forEach(lineReq -> {
            OrderDetail detail = new OrderDetail();
            detail.setSku(lineReq.sku());
            detail.setQuantity(lineReq.quantity());
            detail.setUnitPrice(lineReq.unitPrice());
            order.addLine(detail);
        });
        Order saved = orderRepository.save(order);
        return toResponse(saved);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderResponse.OrderLineResponse> lines = order.getLines().stream()
                .map(d -> new OrderResponse.OrderLineResponse(
                        d.getId(),
                        d.getSku(),
                        d.getQuantity(),
                        d.getUnitPrice()
                ))
                .toList();
        return new OrderResponse(order.getId(), order.getCustomerName(), lines);
    }
}
