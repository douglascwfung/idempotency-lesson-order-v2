package lab.lesson.order.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import lab.lesson.idem.IdempotencyService;
import lab.lesson.order.service.OrderService;
import lab.lesson.order.web.dto.CreateOrderRequest;
import lab.lesson.order.web.dto.OrderResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public OrderController(OrderService orderService,
                           IdempotencyService idempotencyService,
                           ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateOrderRequest request) throws Exception {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return orderService.create(request);
        }

        String userId = currentUserId();
        String rawBody = objectMapper.writeValueAsString(request);
        // Domain work runs inside runOrReplay's transaction as a lambda.
        return idempotencyService.runOrReplay(
                idempotencyKey,
                userId,
                "/orders",
                rawBody,
                201,
                () -> orderService.create(request),
                objectMapper::writeValueAsString,
                body -> objectMapper.readValue(body, OrderResponse.class));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.get(id);
    }

    @PutMapping("/{id}")
    public OrderResponse replace(@PathVariable Long id, @RequestBody CreateOrderRequest request) {
        return orderService.replace(id, request);
    }

    @PostMapping("/{id}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse.OrderLineResponse addLine(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @RequestBody CreateOrderRequest.OrderLineRequest request) throws Exception {

        String userId = currentUserId();
        String path = "/orders/" + id + "/lines";
        String rawBody = objectMapper.writeValueAsString(request);
        // Idempotency-Key is required (no non-idempotent path).
        return idempotencyService.runOrReplay(
                idempotencyKey,
                userId,
                path,
                rawBody,
                201,
                () -> orderService.addLine(id, request),
                objectMapper::writeValueAsString,
                body -> objectMapper.readValue(body, OrderResponse.OrderLineResponse.class));
    }

    @PutMapping("/{id}/lines/{lineId}")
    public OrderResponse.OrderLineResponse updateLine(@PathVariable Long id,
                                                      @PathVariable Long lineId,
                                                      @RequestParam Integer version,
                                                      @RequestBody CreateOrderRequest.OrderLineRequest request) {
        return orderService.updateLine(id, lineId, version, request);
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLine(@PathVariable Long id, @PathVariable Long lineId) {
        orderService.deleteLine(id, lineId);
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return "anonymous";
        }
        return authentication.getName();
    }
}
