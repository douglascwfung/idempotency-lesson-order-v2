package lab.lesson.order.service;

import lab.lesson.idem.PermanentRejection;
import lab.lesson.order.domain.Order;
import lab.lesson.order.domain.OrderDetail;
import lab.lesson.order.exception.OrderNotFoundException;
import lab.lesson.order.repository.OrderDetailRepository;
import lab.lesson.order.repository.OrderRepository;
import lab.lesson.order.web.dto.CreateOrderRequest;
import lab.lesson.order.web.dto.OrderResponse;
import lab.lesson.order.web.dto.SaveOrderChangesRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    /**
     * Mixed add + update used as the work lambda body inside
     * {@code IdempotencyService.runOrReplay}. No separate transaction is required;
     * when called from the lambda it joins the key method's open transaction.
     *
     * <p>Early version mismatches throw {@link PermanentRejection} before any line
     * is changed so the key method can store FAILED. A database optimistic-lock
     * failure must not be caught here — it must leave {@code runOrReplay} so the
     * transaction rolls back.
     */
    public OrderResponse saveChanges(Long orderId, SaveOrderChangesRequest request) {
        return applyChanges(orderId, request);
    }

    /**
     * Manual version check, before any line is changed. A mismatch throws
     * {@link PermanentRejection}. {@code runOrReplay} may catch that and store FAILED
     * because no business row has changed.
     *
     * <p>{@code @Version} is still the lock. A conflict discovered at flush is not
     * PermanentRejection. Do not catch it here. It must leave {@code runOrReplay}
     * so the line change rolls back.
     *
     * <p>This method has no {@code @Transactional}. It still runs in the transaction
     * started by {@code runOrReplay}, because {@link #saveChanges} is called from the
     * lambda that method is executing. {@link #insertLine} is a plain helper in this
     * class — no annotation needed; the insert commits or rolls back with the key method.
     */
    private OrderResponse applyChanges(Long orderId, SaveOrderChangesRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!Objects.equals(request.version(), order.getVersion())) {
            throw new PermanentRejection(409, "{\"error\":\"order version conflict\"}");
        }
        List<OrderDetail> linesToUpdate = new ArrayList<>();
        for (SaveOrderChangesRequest.LineUpdate update : request.updates()) {
            OrderDetail detail = orderDetailRepository.findByIdAndOrderId(update.lineId(), orderId)
                    .orElseThrow(() -> new OrderNotFoundException(update.lineId()));
            if (!Objects.equals(update.line().version(), detail.getVersion())) {
                throw new PermanentRejection(409, "{\"error\":\"order line version conflict\"}");
            }
            linesToUpdate.add(detail);
        }
        for (int i = 0; i < linesToUpdate.size(); i++) {
            SaveOrderChangesRequest.LineChange line = request.updates().get(i).line();
            linesToUpdate.get(i).setSku(line.sku());
            linesToUpdate.get(i).setQuantity(line.quantity());
            linesToUpdate.get(i).setUnitPrice(line.unitPrice());
        }
        for (CreateOrderRequest.OrderLineRequest addition : request.additions()) {
            insertLine(orderId, addition);
        }
        // Reload so newly inserted lines appear in the response.
        Order refreshed = orderRepository.findById(orderId).orElse(order);
        return toResponse(refreshed);
    }

    /**
     * Plain helper called by {@link #applyChanges}. No transaction starts here.
     * The insert commits or rolls back with {@code runOrReplay}.
     */
    private OrderDetail insertLine(Long orderId, CreateOrderRequest.OrderLineRequest request) {
        OrderDetail detail = new OrderDetail();
        detail.setSku(request.sku());
        detail.setQuantity(request.quantity());
        detail.setUnitPrice(request.unitPrice());
        detail.setOrder(orderRepository.getReferenceById(orderId));
        return orderDetailRepository.save(detail);
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
        return new OrderResponse(order.getId(), order.getCustomerName(), order.getVersion(), lines);
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
