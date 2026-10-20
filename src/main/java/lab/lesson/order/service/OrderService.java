package lab.lesson.order.service;

import lab.lesson.order.domain.Order;
import lab.lesson.order.domain.OrderDetail;
import lab.lesson.order.exception.OrderNotFoundException;
import lab.lesson.order.repository.OrderDetailRepository;
import lab.lesson.order.repository.OrderRepository;
import lab.lesson.order.web.dto.CreateOrderRequest;
import lab.lesson.order.web.dto.OrderResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;

    public OrderService(OrderRepository orderRepository, OrderDetailRepository orderDetailRepository) {
        this.orderRepository = orderRepository;
        this.orderDetailRepository = orderDetailRepository;
    }

    @Transactional
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

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return toResponse(order);
    }

    @Transactional
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
                        d.getUnitPrice(),
                        d.getVersion()
                ))
                .toList();
        return new OrderResponse(order.getId(), order.getCustomerName(), lines);
    }

    @Transactional
    public OrderResponse.OrderLineResponse addLine(Long orderId, CreateOrderRequest.OrderLineRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        OrderDetail detail = new OrderDetail();
        detail.setSku(request.sku());
        detail.setQuantity(request.quantity());
        detail.setUnitPrice(request.unitPrice());
        detail.setOrder(order);
        OrderDetail saved = orderDetailRepository.save(detail);
        return new OrderResponse.OrderLineResponse(
                saved.getId(), saved.getSku(), saved.getQuantity(), saved.getUnitPrice(), saved.getVersion());
    }

    @Transactional
    public OrderResponse.OrderLineResponse updateLine(Long orderId, Long lineId, Integer expectedVersion,
                                                      CreateOrderRequest.OrderLineRequest request) {
        OrderDetail detail = orderDetailRepository.findByIdAndOrderId(lineId, orderId)
                .orElseThrow(() -> new OrderNotFoundException(lineId));
        if (!detail.getVersion().equals(expectedVersion)) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(OrderDetail.class, lineId);
        }
        detail.setSku(request.sku());
        detail.setQuantity(request.quantity());
        detail.setUnitPrice(request.unitPrice());
        OrderDetail saved = orderDetailRepository.save(detail);
        return new OrderResponse.OrderLineResponse(
                saved.getId(), saved.getSku(), saved.getQuantity(), saved.getUnitPrice(), saved.getVersion());
    }

    @Transactional
    public void deleteLine(Long orderId, Long lineId) {
        OrderDetail detail = orderDetailRepository.findByIdAndOrderId(lineId, orderId)
                .orElseThrow(() -> new OrderNotFoundException(lineId));
        orderDetailRepository.delete(detail);
    }
}
